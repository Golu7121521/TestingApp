import 'dart:convert';
import 'dart:io';

class CloudStreamSource {
  final String title;
  final String quality;
  final String videoUrl;
  final bool isHindi;

  CloudStreamSource({
    required this.title,
    required this.quality,
    required this.videoUrl,
    required this.isHindi,
  });
}

class CloudStreamExtractor {
  static HttpClient _createClient() {
    return HttpClient()
      ..badCertificateCallback = ((X509Certificate cert, String host, int port) => true)
      ..connectionTimeout = const Duration(seconds: 8);
  }

  static Future<List<CloudStreamSource>> fetchDirectHindiStreams({
    required String movieTitle,
    required String year,
  }) async {
    final List<CloudStreamSource> results = [];
    final client = _createClient();

    final cleanQuery = movieTitle
        .replaceAll(RegExp(r'[^\w\s]+'), '')
        .trim()
        .split(' ')
        .take(4)
        .join(' ');

    if (cleanQuery.isEmpty) {
      client.close();
      return results;
    }

    final targetEndpoint = "https://streamapi.cloud/api/search?q=${Uri.encodeComponent(cleanQuery)}";
    final proxies = [
      'https://api.allorigins.win/raw?url=${Uri.encodeComponent(targetEndpoint)}',
      'https://corsproxy.io/?url=${Uri.encodeComponent(targetEndpoint)}',
    ];

    for (final proxyUrl in proxies) {
      try {
        final req = await client.getUrl(Uri.parse(proxyUrl)).timeout(const Duration(seconds: 6));
        req.headers.set(
          'User-Agent',
          'Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36',
        );

        final res = await req.close();
        if (res.statusCode == 200) {
          final body = await res.transform(utf8.decoder).join();
          final dynamic data = json.decode(body);

          if (data is List && data.isNotEmpty) {
            final seenUrls = <String>{};

            for (var item in data) {
              if (item is Map<String, dynamic>) {
                final String title = item['title']?.toString() ?? cleanQuery;
                final String stream = (item['file'] ?? item['url'] ?? '').toString();
                final String quality = item['quality']?.toString() ?? '1080p';

                final isPlayable = stream.contains('.mp4') ||
                    stream.contains('.mkv') ||
                    stream.contains('.m3u8');

                if (isPlayable && !seenUrls.contains(stream)) {
                  seenUrls.add(stream);
                  final lowerTitle = title.toLowerCase();
                  final isHindi = lowerTitle.contains('hindi') ||
                      lowerTitle.contains('hin') ||
                      lowerTitle.contains('dub');

                  results.add(
                    CloudStreamSource(
                      title: title,
                      quality: quality,
                      videoUrl: stream,
                      isHindi: isHindi || true,
                    ),
                  );
                }
              }
            }
          }
        }
        if (results.isNotEmpty) break;
      } catch (_) {}
    }

    client.close();
    return results;
  }
}
