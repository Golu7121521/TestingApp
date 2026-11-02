import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

class RootNav {
  static const int downloadsTab = 2;
  static final ValueNotifier<int> index = ValueNotifier<int>(0);
}

String formatBytes(num bytes) {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  double v = bytes.toDouble();
  int i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return '${v.toStringAsFixed(i >= 3 ? 1 : 0)} ${units[i]}';
}

enum DownloadStatus { queued, downloading, paused, completed, error }

class StorageSpace {
  const StorageSpace(this.totalBytes, this.usedBytes);
  final int totalBytes;
  final int usedBytes;
  double get fraction => totalBytes == 0 ? 0 : usedBytes / totalBytes;
  String get label => '${formatBytes(usedBytes)} of ${formatBytes(totalBytes)} used';
}

class DownloadTask {
  DownloadTask({
    required this.id,
    required this.title,
    required this.magnetUri,
    required this.quality,
    required this.audioLanguage,
    required this.sizeLabel,
    this.posterUrl,
    this.status = DownloadStatus.queued,
    this.progress = 0,
    this.speedLabel = '0 KB/s',
    this.stateLabel = 'Starting…',
    this.seeds = 0,
    this.filePath,
    this.error,
    DateTime? createdAt,
  }) : createdAt = createdAt ?? DateTime.now();

  final String id;
  final String title;
  final String magnetUri;
  final String quality;
  final String audioLanguage;
  final String sizeLabel;
  final String? posterUrl;
  final DateTime createdAt;

  DownloadStatus status;
  double progress;
  String speedLabel;
  String stateLabel;
  int seeds;
  String? filePath;
  String? error;

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'magnetUri': magnetUri,
        'quality': quality,
        'audioLanguage': audioLanguage,
        'sizeLabel': sizeLabel,
        'posterUrl': posterUrl,
        'status': status.name,
        'progress': progress,
        'filePath': filePath,
        'error': error,
        'createdAt': createdAt.toIso8601String(),
      };

  factory DownloadTask.fromJson(Map<String, dynamic> j) => DownloadTask(
        id: j['id'] as String,
        title: j['title'] as String,
        magnetUri: j['magnetUri'] as String,
        quality: j['quality'] as String? ?? '',
        audioLanguage: j['audioLanguage'] as String? ?? '',
        sizeLabel: j['sizeLabel'] as String? ?? '',
        posterUrl: j['posterUrl'] as String?,
        status: DownloadStatus.values.firstWhere(
          (s) => s.name == j['status'],
          orElse: () => DownloadStatus.queued,
        ),
        progress: (j['progress'] as num?)?.toDouble() ?? 0,
        filePath: j['filePath'] as String?,
        error: j['error'] as String?,
        createdAt: DateTime.tryParse(j['createdAt'] as String? ?? ''),
      );
}

class DownloadManager extends ChangeNotifier {
  DownloadManager._();
  static final DownloadManager instance = DownloadManager._();

  static const _channel = MethodChannel('com.cineflix.app/torrent');
  static const _prefsKey = 'download_tasks_v1';

  final Map<String, DownloadTask> _tasks = {};
  Timer? _timer;
  bool _initialized = false;
  bool _polling = false;
  int _tick = 0;

  List<DownloadTask> get all => _tasks.values.toList();
  List<DownloadTask> get active => all
      .where((t) => t.status != DownloadStatus.completed)
      .toList()
    ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
  List<DownloadTask> get completed => all
      .where((t) => t.status == DownloadStatus.completed)
      .toList()
    ..sort((a, b) => b.createdAt.compareTo(a.createdAt));

  Future<void> init() async {
    if (_initialized) return;
    _initialized = true;

    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_prefsKey);
    if (raw != null) {
      try {
        for (final e in (jsonDecode(raw) as List)) {
          final t = DownloadTask.fromJson(Map<String, dynamic>.from(e as Map));
          _tasks[t.id] = t;
        }
      } catch (e) {
        debugPrint('DownloadManager: corrupt prefs, ignoring ($e)');
      }
    }

    for (final t in _tasks.values.toList()) {
      if (t.status == DownloadStatus.downloading ||
          t.status == DownloadStatus.queued) {
        t.status = DownloadStatus.downloading;
        unawaited(_nativeStart(t));
      }
    }
    notifyListeners();
    _ensureTimer();
  }

  Future<bool> startDownload({
    required String id,
    required String title,
    required String magnetUri,
    required String quality,
    required String audioLanguage,
    required String sizeLabel,
    String? posterUrl,
  }) async {
    final existing = _tasks[id];
    if (existing != null && existing.status != DownloadStatus.error) {
      return true;
    }
    final task = DownloadTask(
      id: id,
      title: title,
      magnetUri: magnetUri,
      quality: quality,
      audioLanguage: audioLanguage,
      sizeLabel: sizeLabel,
      posterUrl: posterUrl,
      status: DownloadStatus.downloading,
    );
    _tasks[id] = task;
    notifyListeners();
    await _persist();
    final ok = await _nativeStart(task);
    _ensureTimer();
    return ok;
  }

  Future<bool> _nativeStart(DownloadTask t) async {
    try {
      await _channel.invokeMethod('startDownloadTask', {
        'id': t.id,
        'magnetUri': t.magnetUri,
        'savePath': '',
      });
      return true;
    } on PlatformException catch (e) {
      t.status = DownloadStatus.error;
      t.error = e.message ?? 'Failed to start';
      notifyListeners();
      await _persist();
      return false;
    }
  }

  Future<void> pause(String id) async {
    final t = _tasks[id];
    if (t == null) return;
    try {
      await _channel.invokeMethod('pauseDownloadTask', {'id': id});
    } on PlatformException catch (e) {
      debugPrint('pause failed: $e');
    }
    t.status = DownloadStatus.paused;
    t.speedLabel = '0 KB/s';
    t.stateLabel = 'Paused';
    notifyListeners();
    await _persist();
  }

  Future<void> resume(String id) async {
    final t = _tasks[id];
    if (t == null) return;
    t.status = DownloadStatus.downloading;
    t.error = null;
    notifyListeners();
    bool ok = false;
    try {
      ok = (await _channel.invokeMethod<bool>('resumeDownloadTask', {'id': id})) ?? false;
    } on PlatformException catch (_) {}
    if (!ok) await _nativeStart(t);
    await _persist();
    _ensureTimer();
  }

  Future<void> remove(String id, {bool deleteFiles = true}) async {
    try {
      await _channel.invokeMethod('deleteDownloadTask', {
        'id': id,
        'deleteFiles': deleteFiles,
      });
    } on PlatformException catch (e) {
      debugPrint('delete failed: $e');
    }
    _tasks.remove(id);
    notifyListeners();
    await _persist();
  }

  Future<StorageSpace> getStorageSpace() async {
    final m = await _channel.invokeMapMethod<String, dynamic>('getDiskSpace');
    return StorageSpace(
      (m?['totalBytes'] as num?)?.toInt() ?? 0,
      (m?['usedBytes'] as num?)?.toInt() ?? 0,
    );
  }

  void _ensureTimer() {
    _timer ??= Timer.periodic(const Duration(seconds: 1), (_) => _poll());
  }

  Future<void> _poll() async {
    if (_polling) return;
    final running = _tasks.values
        .where((t) => t.status == DownloadStatus.downloading)
        .toList();
    if (running.isEmpty) return;

    _polling = true;
    var dirty = false;
    var needsPersist = false;
    try {
      for (final t in running) {
        try {
          final m = await _channel.invokeMapMethod<String, dynamic>(
            'getDownloadStatus',
            {'id': t.id},
          );
          if (m == null) continue;

          if (m['exists'] == false) {
            await _nativeStart(t);
            continue;
          }

          final progress = (m['progress'] as num).toDouble();
          if ((progress - t.progress).abs() > 0.0005) dirty = true;
          t.progress = progress;
          t.speedLabel = m['speed'] as String? ?? t.speedLabel;
          t.stateLabel = m['state'] as String? ?? t.stateLabel;
          t.seeds = (m['seeds'] as num?)?.toInt() ?? t.seeds;

          if (m['isFinished'] == true) {
            t.status = DownloadStatus.completed;
            t.progress = 1;
            t.speedLabel = '';
            t.filePath = m['filePath'] as String?;
            dirty = true;
            needsPersist = true;
          }
        } on PlatformException catch (e) {
          t.status = DownloadStatus.error;
          t.error = e.message;
          dirty = true;
          needsPersist = true;
        }
      }
    } finally {
      _polling = false;
    }

    if (dirty) notifyListeners();
    _tick++;
    if (needsPersist || _tick % 5 == 0) await _persist();
  }

  Future<void> _persist() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _prefsKey,
      jsonEncode(_tasks.values.map((t) => t.toJson()).toList()),
    );
  }
}
