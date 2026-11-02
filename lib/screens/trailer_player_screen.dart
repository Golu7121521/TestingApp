import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:cached_network_image/cached_network_image.dart';

class TrailerPlayerScreen extends StatefulWidget {
  final String initialVideoKey;
  final String title;
  final List<dynamic> allVideos;

  const TrailerPlayerScreen({
    super.key,
    required this.initialVideoKey,
    required this.title,
    this.allVideos = const [],
  });

  @override
  State<TrailerPlayerScreen> createState() => _TrailerPlayerScreenState();
}

class _TrailerPlayerScreenState extends State<TrailerPlayerScreen> {
  late final WebViewController _controller;
  late String _currentVideoKey;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _currentVideoKey = widget.initialVideoKey;

    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);

    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.black)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (_) {
            if (mounted) setState(() => _isLoading = false);
          },
          onNavigationRequest: (request) {
            final url = request.url.toLowerCase();
            if (url.contains('watch?v=') ||
                url.contains('m.youtube.com') ||
                url.contains('youtu.be')) {
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ),
      );

    _loadVideo(_currentVideoKey);
  }

  void _loadVideo(String key) {
    setState(() {
      _currentVideoKey = key;
      _isLoading = true;
    });

    final embedUri = Uri.parse(
      'https://www.youtube-nocookie.com/embed/$key?autoplay=1&playsinline=1&controls=1&modestbranding=1&rel=0&iv_load_policy=3',
    );

    _controller.loadRequest(
      embedUri,
      headers: {'Referer': 'https://www.google.com/'},
    );
  }

  void _restoreOrientation() {
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
    ]);
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  }

  @override
  void dispose() {
    _controller.loadRequest(Uri.parse('about:blank')); // Stop audio playback immediately
    _restoreOrientation();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: true,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) {
          _restoreOrientation();
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(
          children: [
            // Main Video Area
            Positioned.fill(
              child: WebViewWidget(controller: _controller),
            ),

            if (_isLoading)
              const Center(
                child: CircularProgressIndicator(color: Color(0xFFE50914)),
              ),

            // Top Header / Back Button
            Positioned(
              top: 12,
              left: 12,
              child: CircleAvatar(
                backgroundColor: Colors.black54,
                child: IconButton(
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                  onPressed: () => Navigator.pop(context),
                ),
              ),
            ),

            // Bottom More Videos Rail
            if (widget.allVideos.length > 1)
              Positioned(
                left: 0,
                right: 0,
                bottom: 10,
                child: Container(
                  height: 85,
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.bottomCenter,
                      end: Alignment.topCenter,
                      colors: [Colors.black87, Colors.transparent],
                    ),
                  ),
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: widget.allVideos.length,
                    separatorBuilder: (_, __) => const SizedBox(width: 10),
                    itemBuilder: (context, i) {
                      final item = widget.allVideos[i];
                      final key = item['key'] ?? '';
                      final isPlaying = key == _currentVideoKey;

                      return InkWell(
                        onTap: () => _loadVideo(key),
                        child: Container(
                          width: 130,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(6),
                            border: isPlaying
                                ? Border.all(color: const Color(0xFFE50914), width: 2)
                                : null,
                          ),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(5),
                            child: Stack(
                              fit: StackFit.expand,
                              children: [
                                CachedNetworkImage(
                                  imageUrl: 'https://img.youtube.com/vi/$key/mqdefault.jpg',
                                  fit: BoxFit.cover,
                                  placeholder: (_, __) => Container(color: const Color(0xFF1E1E1E)),
                                  errorWidget: (_, __, ___) => Container(color: const Color(0xFF1E1E1E)),
                                ),
                                Container(color: isPlaying ? Colors.transparent : Colors.black45),
                                Center(
                                  child: Icon(
                                    isPlaying ? Icons.equalizer_rounded : Icons.play_arrow_rounded,
                                    color: Colors.white,
                                    size: 26,
                                  ),
                                ),
                                Positioned(
                                  bottom: 4,
                                  left: 4,
                                  right: 4,
                                  child: Text(
                                    item['name'] ?? 'Clip',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 9,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                )
                              ],
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
