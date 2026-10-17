import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import 'store.dart';

class PlayerPage extends StatefulWidget {
  final VideoItem item;
  final bool fromStart;
  const PlayerPage({super.key, required this.item, this.fromStart = false});
  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> with WidgetsBindingObserver {
  VideoPlayerController? _c;
  String? _error;
  bool _controls = true, _locked = false;
  double _speed = 1.0;
  Timer? _hide, _saveTimer;
  String _osd = '';
  Timer? _osdTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    WakelockPlus.enable();
    _init();
  }

  Future<void> _init() async {
    try {
      final f = await widget.item.file();
      if (f == null || !await f.exists()) {
        setState(() => _error = 'File not found or no longer accessible.');
        return;
      }
      final c = VideoPlayerController.file(f);
      await c.initialize();
      if (!widget.fromStart) {
        final p = await PlaybackStore.position(widget.item.id);
        if (p > 0 && p < c.value.duration.inMilliseconds - 5000) {
          await c.seekTo(Duration(milliseconds: p));
        }
      }
      c.addListener(() {
        if (mounted) setState(() {});
        if (c.value.hasError && _error == null) {
          setState(() => _error = c.value.errorDescription);
        }
        if (c.value.isCompleted) _save();
      });
      _c = c;
      await c.play();
      _saveTimer = Timer.periodic(const Duration(seconds: 5), (_) => _save());
      _scheduleHide();
      setState(() {});
    } catch (e) {
      setState(() => _error =
          'This file cannot be played. The format or codec may not be supported on this device.');
    }
  }

  Future<void> _save() async {
    final c = _c;
    if (c == null || !c.value.isInitialized) return;
    await PlaybackStore.save(widget.item.id, c.value.position.inMilliseconds,
        c.value.duration.inMilliseconds);
  }

  void _scheduleHide() {
    _hide?.cancel();
    _hide = Timer(const Duration(seconds: 4), () {
      if (mounted && (_c?.value.isPlaying ?? false)) {
        setState(() => _controls = false);
      }
    });
  }

  void _seekBy(int s) {
    final c = _c!;
    final t = c.value.position + Duration(seconds: s);
    final max = c.value.duration;
    c.seekTo(t < Duration.zero ? Duration.zero : (t > max ? max : t));
    _showOsd(s > 0 ? '+${s}s' : '${s}s');
  }

  void _showOsd(String t) {
    setState(() => _osd = t);
    _osdTimer?.cancel();
    _osdTimer = Timer(const Duration(milliseconds: 700),
        () => mounted ? setState(() => _osd = '') : null);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.paused) {
      _c?.pause();
      _save();
    }
  }

  @override
  void dispose() {
    _save();
    _hide?.cancel();
    _saveTimer?.cancel();
    _osdTimer?.cancel();
    _c?.dispose();
    WakelockPlus.disable();
    WidgetsBinding.instance.removeObserver(this);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    SystemChrome.setPreferredOrientations([]);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = _c;
    if (_error != null) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.item.title)),
        body: Center(
            child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(_error!, textAlign: TextAlign.center),
        )),
      );
    }
    if (c == null || !c.value.isInitialized) {
      return const Scaffold(
          backgroundColor: Colors.black,
          body: Center(child: CircularProgressIndicator()));
    }
    final v = c.value;
    return Scaffold(
      backgroundColor: Colors.black,
      body: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () {
          setState(() => _controls = !_controls);
          if (_controls) _scheduleHide();
        },
        onDoubleTapDown: _locked
            ? null
            : (d) {
                final left = d.localPosition.dx < MediaQuery.of(context).size.width / 2;
                _seekBy(left ? -10 : 10);
              },
        onDoubleTap: () {},
        child: Stack(children: [
          Center(
            child: AspectRatio(
                aspectRatio: v.aspectRatio, child: VideoPlayer(c)),
          ),
          if (v.isBuffering)
            const Center(child: CircularProgressIndicator()),
          if (_osd.isNotEmpty)
            Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                    color: Colors.black54, borderRadius: BorderRadius.circular(16)),
                child: Text(_osd, style: const TextStyle(fontSize: 20)),
              ),
            ),
          if (_controls) ..._overlay(c),
          if (_locked && _controls)
            Positioned(
              right: 16, top: 0, bottom: 0,
              child: Center(
                child: IconButton.filled(
                  tooltip: 'Unlock',
                  icon: const Icon(Icons.lock),
                  onPressed: () => setState(() => _locked = false),
                ),
              ),
            ),
        ]),
      ),
    );
  }

  List<Widget> _overlay(VideoPlayerController c) {
    if (_locked) return [];
    final v = c.value;
    final pos = v.position.inSeconds;
    final dur = v.duration.inSeconds;
    return [
      Positioned(
        top: 0, left: 0, right: 0,
        child: Container(
          padding: const EdgeInsets.fromLTRB(4, 28, 4, 24),
          decoration: const BoxDecoration(
              gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Color(0xCC000000), Colors.transparent])),
          child: Row(children: [
            IconButton(
                tooltip: 'Back',
                icon: const Icon(Icons.arrow_back),
                onPressed: () => Navigator.pop(context)),
            Expanded(
                child: Text(widget.item.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 16))),
            IconButton(
                tooltip: 'Lock controls',
                icon: const Icon(Icons.lock_open),
                onPressed: () => setState(() => _locked = true)),
            PopupMenuButton<double>(
              tooltip: 'Playback speed',
              icon: const Icon(Icons.speed),
              initialValue: _speed,
              onSelected: (s) {
                _speed = s;
                c.setPlaybackSpeed(s);
                _showOsd('${s}x');
              },
              itemBuilder: (_) => [
                for (final s in [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0])
                  PopupMenuItem(value: s, child: Text('${s}x'))
              ],
            ),
            IconButton(
                tooltip: 'Rotate',
                icon: const Icon(Icons.screen_rotation),
                onPressed: () {
                  final land = MediaQuery.of(context).orientation ==
                      Orientation.landscape;
                  SystemChrome.setPreferredOrientations(land
                      ? [DeviceOrientation.portraitUp]
                      : [
                          DeviceOrientation.landscapeLeft,
                          DeviceOrientation.landscapeRight
                        ]);
                }),
          ]),
        ),
      ),
      Center(
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          IconButton(
              iconSize: 40,
              tooltip: 'Back 10 seconds',
              icon: const Icon(Icons.replay_10),
              onPressed: () => _seekBy(-10)),
          const SizedBox(width: 16),
          IconButton.filled(
            iconSize: 52,
            tooltip: v.isPlaying ? 'Pause' : 'Play',
            icon: Icon(v.isPlaying ? Icons.pause : Icons.play_arrow),
            onPressed: () {
              v.isPlaying ? c.pause() : c.play();
              if (v.isPlaying) _save();
              _scheduleHide();
            },
          ),
          const SizedBox(width: 16),
          IconButton(
              iconSize: 40,
              tooltip: 'Forward 10 seconds',
              icon: const Icon(Icons.forward_10),
              onPressed: () => _seekBy(10)),
        ]),
      ),
      Positioned(
        bottom: 0, left: 0, right: 0,
        child: Container(
          padding: const EdgeInsets.fromLTRB(16, 24, 16, 16),
          decoration: const BoxDecoration(
              gradient: LinearGradient(
                  begin: Alignment.bottomCenter,
                  end: Alignment.topCenter,
                  colors: [Color(0xCC000000), Colors.transparent])),
          child: Row(children: [
            Text(fmt(pos)),
            Expanded(
              child: Slider(
                value: pos.clamp(0, dur == 0 ? 1 : dur).toDouble(),
                max: dur == 0 ? 1 : dur.toDouble(),
                onChanged: (x) => c.seekTo(Duration(seconds: x.toInt())),
                onChangeEnd: (_) => _save(),
              ),
            ),
            Text(fmt(dur)),
          ]),
        ),
      ),
    ];
  }
}
