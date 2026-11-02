import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:better_player_plus/better_player_plus.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import 'package:webview_flutter/webview_flutter.dart';

class ExoPlayerScreen extends StatefulWidget {
  final String videoUrl;
  final String title;
  final Map<String, String>? headers;

  const ExoPlayerScreen({
    super.key,
    required this.videoUrl,
    required this.title,
    this.headers,
  });

  @override
  State<ExoPlayerScreen> createState() => _ExoPlayerScreenState();
}

class _ExoPlayerScreenState extends State<ExoPlayerScreen> {
  BetterPlayerController? _controller;
  WebViewController? _webViewController;
  bool _isLoading = true;
  String? _errorMessage;
  bool _isEmbedStream = false;

  @override
  void initState() {
    super.initState();

    // 1. Force landscape orientation and keep screen on
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    WakelockPlus.enable();

    // 2. Check if the URL is an embed/web iframe or a direct stream
    final url = widget.videoUrl.toLowerCase();
    if (url.contains('vidsrc') || url.contains('/embed') || url.contains('.html')) {
      _isEmbedStream = true;
      _initWebviewPlayer();
    } else {
      _initializeExoPlayer();
    }
  }

  void _initWebviewPlayer() {
    try {
      _webViewController = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setBackgroundColor(Colors.black)
        ..setNavigationDelegate(
          NavigationDelegate(
            onPageFinished: (_) {
              if (mounted) setState(() => _isLoading = false);
            },
            onWebResourceError: (error) {
              if (mounted) {
                setState(() {
                  _isLoading = false;
                  _errorMessage = "Embed Player Error: ${error.description}";
                });
              }
            },
          ),
        )
        ..loadRequest(
          Uri.parse(widget.videoUrl),
          headers: widget.headers ?? {
            'User-Agent':
                'Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36',
          },
        );
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorMessage = e.toString();
        });
      }
    }
  }

  void _initializeExoPlayer() {
    try {
      final isHls = widget.videoUrl.contains('.m3u8');

      final BetterPlayerConfiguration playerConfig = BetterPlayerConfiguration(
        aspectRatio: 16 / 9,
        fit: BoxFit.contain,
        autoPlay: true,
        looping: false,
        fullScreenByDefault: false,
        allowedScreenSleep: false,
        autoDispose: true,
        controlsConfiguration: const BetterPlayerControlsConfiguration(
          enableAudioTracks: true,
          enableSubtitles: true,
          enableQualities: true,
          enablePlaybackSpeed: true,
          enablePip: true,
          backgroundColor: Colors.black54,
          controlBarColor: Colors.black87,
          iconsColor: Colors.white,
          progressBarPlayedColor: Color(0xFFE50914),
          progressBarHandleColor: Color(0xFFE50914),
          progressBarBufferedColor: Colors.white24,
          progressBarBackgroundColor: Colors.white10,
          loadingColor: Color(0xFFE50914),
          overflowModalColor: Color(0xFF1E1E1E),
          overflowModalTextColor: Colors.white,
          overflowMenuIconsColor: Colors.white70,
        ),
      );

      final BetterPlayerDataSource dataSource = BetterPlayerDataSource(
        BetterPlayerDataSourceType.network,
        widget.videoUrl,
        videoFormat: isHls ? BetterPlayerVideoFormat.hls : BetterPlayerVideoFormat.other,
        useAsmsSubtitles: true,
        useAsmsTracks: true,
        headers: widget.headers ?? {
          'User-Agent':
              'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
        },
        notificationConfiguration: BetterPlayerNotificationConfiguration(
          showNotification: true,
          title: widget.title,
          author: "CineFlix",
        ),
      );

      _controller = BetterPlayerController(playerConfig);
      _controller!.setupDataSource(dataSource).then((_) {
        if (mounted) {
          setState(() => _isLoading = false);
        }
      }).catchError((err) {
        if (mounted) {
          setState(() {
            _isLoading = false;
            _errorMessage = "Playback Error: Stream format unsupported or link offline.";
          });
        }
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorMessage = e.toString();
        });
      }
    }
  }

  @override
  void dispose() {
    WakelockPlus.disable();
    _controller?.clearCache();
    _controller?.dispose();
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: true,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) {
          WakelockPlus.disable();
          SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(
          children: [
            // Native Player
            if (!_isEmbedStream && _controller != null && _errorMessage == null)
              Center(
                child: BetterPlayer(controller: _controller!),
              ),

            // Embed Webview Player Fallback
            if (_isEmbedStream && _webViewController != null && _errorMessage == null)
              WebViewWidget(controller: _webViewController!),

            // Loading Indicator
            if (_isLoading)
              Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const CircularProgressIndicator(color: Color(0xFFE50914)),
                    const SizedBox(height: 14),
                    Text(
                      _isEmbedStream
                          ? "Connecting to Cloud Stream..."
                          : "Connecting to ExoPlayer Stream...",
                      style: TextStyle(
                        color: Colors.white.withOpacity(0.8),
                        fontSize: 13,
                        letterSpacing: 0.5,
                      ),
                    ),
                  ],
                ),
              ),

            // Error Fallback View
            if (_errorMessage != null)
              Center(
                child: Container(
                  padding: const EdgeInsets.all(24),
                  margin: const EdgeInsets.symmetric(horizontal: 24),
                  decoration: BoxDecoration(
                    color: const Color(0xFF161616),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.white12),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.error_outline_rounded,
                        color: Color(0xFFE50914),
                        size: 46,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        _errorMessage!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Colors.white70, fontSize: 13),
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFFE50914),
                          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                        ),
                        onPressed: () {
                          setState(() {
                            _isLoading = true;
                            _errorMessage = null;
                          });
                          if (_isEmbedStream) {
                            _initWebviewPlayer();
                          } else {
                            _initializeExoPlayer();
                          }
                        },
                        child: const Text("Retry", style: TextStyle(color: Colors.white)),
                      ),
                    ],
                  ),
                ),
              ),

            // Floating Back Button
            Positioned(
              top: 20,
              left: 20,
              child: CircleAvatar(
                backgroundColor: Colors.black54,
                child: IconButton(
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                  onPressed: () => Navigator.pop(context),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
