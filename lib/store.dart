import 'dart:io';
import 'dart:typed_data';
import 'package:photo_manager/photo_manager.dart';
import 'package:shared_preferences/shared_preferences.dart';

class VideoItem {
  final String id;
  final String title;
  final int durationSec;
  final String folder;
  final DateTime added;
  final AssetEntity? asset;
  final String? path;

  VideoItem({
    required this.id,
    required this.title,
    required this.durationSec,
    required this.folder,
    required this.added,
    this.asset,
    this.path,
  });

  Future<File?> file() async => asset != null ? asset!.file : File(path!);
}

/// Playback position + history, stored locally. Nothing leaves the device.
class PlaybackStore {
  static Future<SharedPreferences> get _p => SharedPreferences.getInstance();

  static Future<int> position(String id) async =>
      (await _p).getInt('pos_$id') ?? 0;

  static Future<void> save(String id, int posMs, int durMs) async {
    final p = await _p;
    final done = durMs > 0 && posMs > durMs - 5000;
    await p.setInt('pos_$id', done ? 0 : posMs);
    await p.setInt('dur_$id', durMs);
    final recent = p.getStringList('recent') ?? [];
    recent.remove(id);
    recent.insert(0, id);
    await p.setStringList('recent', recent.take(30).toList());
  }

  static Future<List<String>> recent() async =>
      (await _p).getStringList('recent') ?? [];

  static Future<double> progress(String id) async {
    final p = await _p;
    final d = p.getInt('dur_$id') ?? 0;
    final pos = p.getInt('pos_$id') ?? 0;
    return d == 0 ? 0 : (pos / d).clamp(0.0, 1.0);
  }

  static Future<void> clearHistory() async {
    final p = await _p;
    for (final k in p.getKeys().toList()) {
      if (k.startsWith('pos_') || k.startsWith('dur_') || k == 'recent') {
        await p.remove(k);
      }
    }
  }
}

class VideoScanner {
  /// Reads the Android MediaStore via photo_manager. Returns null if permission denied.
  static Future<List<VideoItem>?> scan() async {
    final perm = await PhotoManager.requestPermissionExtend(
      requestOption: const PermissionRequestOption(
        androidPermission:
            AndroidPermission(type: RequestType.video, mediaLocation: false),
      ),
    );
    if (!perm.hasAccess) return null;
    final paths = await PhotoManager.getAssetPathList(
        type: RequestType.video, onlyAll: true);
    if (paths.isEmpty) return [];
    final count = await paths.first.assetCountAsync;
    final assets = await paths.first.getAssetListRange(start: 0, end: count);
    final out = <VideoItem>[];
    for (final a in assets) {
      final rel = a.relativePath ?? '';
      final folder = rel.split('/').where((s) => s.isNotEmpty).lastOrNull ?? 'Videos';
      out.add(VideoItem(
        id: a.id,
        title: a.title ?? 'Video',
        durationSec: a.duration,
        folder: folder,
        added: a.createDateTime,
        asset: a,
      ));
    }
    return out;
  }
}

final Map<String, Uint8List?> _thumbCache = {};

Future<Uint8List?> thumbFor(VideoItem v) async {
  if (v.asset == null) return null;
  if (_thumbCache.containsKey(v.id)) return _thumbCache[v.id];
  final d = await v.asset!.thumbnailDataWithSize(const ThumbnailSize(320, 200));
  _thumbCache[v.id] = d;
  return d;
}

String fmt(int sec) {
  final d = Duration(seconds: sec);
  String two(int n) => n.toString().padLeft(2, '0');
  return d.inHours > 0
      ? '${d.inHours}:${two(d.inMinutes % 60)}:${two(d.inSeconds % 60)}'
      : '${two(d.inMinutes)}:${two(d.inSeconds % 60)}';
}
