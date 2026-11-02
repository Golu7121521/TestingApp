import 'dart:async';
import 'package:better_player_plus/better_player_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ExoPlayerScreen extends StatefulWidget {
  const ExoPlayerScreen({super.key, required this.filePath, required this.title});

  final String filePath;
  final String title;

  @override
  State<ExoPlayerScreen> createState() => _ExoPlayerScreenState();
}

class _ExoPlayerScreenState extends State<ExoPlayerScreen> {
  static const _channel = MethodChannel('com.cineflix.app/torrent');
  static const _speeds = [0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0];

  late final BetterPlayerController _controller;

  bool _locked = false;
  double _volume = 0.5;
  double _speed = 1.0;
  String? _hud;
  Timer? _hudTimer;

  @override
  void initState() {
    super.initState();
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);

    _controller = BetterPlayerController(
      const BetterPlayerConfiguration(
        autoPlay: true,
        aspectRatio: 16 / 9,
        fit: BoxFit.contain,
        handleLifecycle: true,
        controlsConfiguration: BetterPlayerControlsConfiguration(
          enableFullscreen: false,
          enableOverflowMenu: false,
          enableSkips: true,
        ),
      ),
      betterPlayerDataSource: BetterPlayerDataSource(
        BetterPlayerDataSourceType.file,
        widget.filePath,
      ),
    );
  }

  @override
  void dispose() {
    _hudTimer?.cancel();
    _controller.dispose();
    SystemChrome.setPreferredOrientations(DeviceOrientation.values);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  void _showHud(String text) {
    setState(() => _hud = text);
    _hudTimer?.cancel();
    _hudTimer = Timer(const Duration(milliseconds: 800), () {
      if (mounted) setState(() => _hud = null);
    });
  }

  void _onDragUpdate(DragUpdateDetails d, double height) {
    final delta = -d.delta.dy / (height * 0.7);
    _volume = (_volume + delta).clamp(0.0, 1.0);
    _controller.setVolume(_volume);
    _showHud('Volume ${(_volume * 100).round()}%');
  }

  Future<void> _pickSpeed() async {
    final picked = await showDialog<double>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('Playback speed'),
        children: [
          for (final s in _speeds)
            RadioListTile<double>(
              value: s,
              groupValue: _speed,
              title: Text(s == 1.0 ? 'Normal (1.0x)' : '${s}x'),
              onChanged: (v) => Navigator.pop(ctx, v),
            ),
        ],
      ),
    );
    if (picked != null) {
      await _controller.setSpeed(picked);
      setState(() => _speed = picked);
    }
  }

  Future<void> _pickAudioTrack() async {
    List<Map<String, dynamic>> tracks = [];
    try {
      final raw = await _channel.invokeListMethod<Map>(
        'listAudioTracks',
        {'path': widget.filePath},
      );
      tracks = (raw ?? []).map((m) => Map<String, dynamic>.from(m)).toList();
    } on PlatformException catch (_) {}

    if (!mounted) return;
    if (tracks.length < 2) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('This file has a single audio track.')),
      );
      return;
    }

    final picked = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('Audio track'),
        children: [
          for (final t in tracks)
            ListTile(
              leading: const Icon(Icons.audiotrack),
              title: Text(_languageName(t['language'] as String)),
              subtitle: Text('Track ${(t['index'] as int) + 1} · ${t['mime']}'
                  '${t['channels'] != null ? ' · ${t['channels']}ch' : ''}'),
              onTap: () => Navigator.pop(ctx, t),
            ),
        ],
      ),
    );
    if (picked == null) return;

    _controller.setAudioTrack(BetterPlayerAsmsAudioTrack(
      id: picked['index'] as int,
      label: picked['language'] as String,
      language: picked['language'] as String,
    ));
    _showHud('Audio: ${_languageName(picked['language'] as String)}');
  }

  String _languageName(String code) {
    const names = {
      'eng': 'English', 'en': 'English', 'hin': 'Hindi', 'hi': 'Hindi',
      'spa': 'Spanish', 'es': 'Spanish', 'fra': 'French', 'fre': 'French',
      'fr': 'French', 'deu': 'German', 'ger': 'German', 'de': 'German',
      'jpn': 'Japanese', 'ja': 'Japanese', 'kor': 'Korean', 'ko': 'Korean',
      'tam': 'Tamil', 'tel': 'Telugu', 'por': 'Portuguese', 'rus': 'Russian',
      'ita': 'Italian', 'zho': 'Chinese', 'chi': 'Chinese', 'und': 'Unknown',
    };
    return names[code.toLowerCase()] ?? code.toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: LayoutBuilder(builder: (context, c) {
        return Stack(
          fit: StackFit.expand,
          children: [
            IgnorePointer(
              ignoring: _locked,
              child: Center(child: BetterPlayer(controller: _controller)),
            ),

            if (!_locked)
              Positioned(
                left: 0,
                right: 0,
                top: c.maxHeight * 0.15,
                bottom: c.maxHeight * 0.15,
                child: GestureDetector(
                  behavior: HitTestBehavior.translucent,
                  onVerticalDragUpdate: (d) => _onDragUpdate(d, c.maxHeight),
                ),
              ),

            if (!_locked)
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [Colors.black54, Colors.transparent],
                    ),
                  ),
                  child: SafeArea(
                    bottom: false,
                    child: Row(children: [
                      IconButton(
                        color: Colors.white,
                        icon: const Icon(Icons.arrow_back),
                        onPressed: () => Navigator.of(context).maybePop(),
                      ),
                      Expanded(
                        child: Text(widget.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(color: Colors.white, fontSize: 16)),
                      ),
                      IconButton(
                        tooltip: 'Audio track',
                        color: Colors.white,
                        icon: const Icon(Icons.audiotrack),
                        onPressed: _pickAudioTrack,
                      ),
                      TextButton(
                        onPressed: _pickSpeed,
                        child: Text('${_speed}x',
                            style: const TextStyle(color: Colors.white)),
                      ),
                    ]),
                  ),
                ),
              ),

            Positioned(
              left: 12,
              top: 0,
              bottom: 0,
              child: Center(
                child: Material(
                  color: Colors.black45,
                  shape: const CircleBorder(),
                  child: IconButton(
                    color: Colors.white,
                    tooltip: _locked ? 'Unlock' : 'Lock controls',
                    icon: Icon(_locked ? Icons.lock : Icons.lock_open),
                    onPressed: () {
                      setState(() => _locked = !_locked);
                      _showHud(_locked ? 'Locked' : 'Unlocked');
                    },
                  ),
                ),
              ),
            ),

            if (_hud != null)
              Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  decoration: BoxDecoration(
                    color: Colors.black54,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(_hud!, style: const TextStyle(color: Colors.white)),
                ),
              ),
          ],
        );
      }),
    );
  }
}
