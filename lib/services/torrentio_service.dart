import 'dart:convert';
import 'package:http/http.dart' as http;

class TorrentStreamItem {
  final String title;
  final String quality;
  final String size;
  final String onlineStreamUrl;
  final bool isHindi;
  final bool isCam;

  TorrentStreamItem({
    required this.title,
    required this.quality,
    required this.size,
    required this.onlineStreamUrl,
    required this.isHindi,
    required this.isCam,
  });
}

class TorrentioService {
  static const String _tmdbApiKey = "bda15b72d8122a53b19ef34ab5f04523";
  static final http.Client _client = http.Client();

  static const List<String> _torrentioMirrors = [
    'https://torrentio.strem.fun',
    'https://torrentio.stremio.net',
  ];

  // 1. Resolve IMDb ID using TMDB ID
  static Future<String?> getImdbId(
    int tmdbId,
    String mediaType, {
    String? movieTitle,
  }) async {
    try {
      final endpoint = mediaType == 'tv' ? 'tv' : 'movie';
      final url = Uri.parse(
        'https://api.themoviedb.org/3/$endpoint/$tmdbId/external_ids?api_key=$_tmdbApiKey',
      );

      final response = await _client.get(url).timeout(const Duration(seconds: 8));

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        final String? imdbId = data['imdb_id'];
        if (imdbId != null && imdbId.isNotEmpty) {
          return imdbId;
        }
      }
    } catch (_) {}

    // Fallback: Title-based IMDb resolution via Suggester API
    if (movieTitle != null && movieTitle.isNotEmpty) {
      try {
        final cleanTitle = Uri.encodeComponent(movieTitle.toLowerCase().trim());
        final searchUrl = Uri.parse(
          'https://v3.sg.media-imdb.com/suggestion/x/$cleanTitle.json',
        );
        final res = await _client.get(searchUrl).timeout(const Duration(seconds: 6));
        if (res.statusCode == 200) {
          final data = json.decode(res.body);
          final list = data['d'] as List?;
          if (list != null && list.isNotEmpty) {
            final firstMatch = list.firstWhere(
              (item) => item['id'] != null && item['id'].toString().startsWith('tt'),
              orElse: () => list.first,
            );
            return firstMatch['id']?.toString();
          }
        }
      } catch (_) {}
    }

    return null;
  }

  // 2. Fetch playable streams from Torrentio
  static Future<List<TorrentStreamItem>> fetchStreams({
    required String imdbId,
    bool isTV = false,
    int season = 1,
    int episode = 1,
  }) async {
    final List<TorrentStreamItem> results = [];

    final String streamPath = isTV
        ? 'stream/series/$imdbId:$season:$episode.json'
        : 'stream/movie/$imdbId.json';

    for (final baseMirror in _torrentioMirrors) {
      try {
        final url = Uri.parse('$baseMirror/$streamPath');
        final response = await _client.get(url).timeout(const Duration(seconds: 8));

        if (response.statusCode == 200) {
          final data = json.decode(response.body);
          final streams = data['streams'] as List?;

          if (streams != null && streams.isNotEmpty) {
            for (var item in streams) {
              final String rawTitle = (item['title'] ?? '').toString();
              final String name = (item['name'] ?? '').toString();
              final String infoHash = (item['infoHash'] ?? '').toString();
              final int? fileIdx = item['fileIdx'] as int?;

              if (infoHash.isEmpty) continue;

              String quality = 'HD';
              final lowerTitle = rawTitle.toLowerCase();
              if (lowerTitle.contains('4k') || lowerTitle.contains('2160p')) {
                quality = '4K';
              } else if (lowerTitle.contains('1080p')) {
                quality = '1080p';
              } else if (lowerTitle.contains('720p')) {
                quality = '720p';
              } else if (lowerTitle.contains('480p')) {
                quality = '480p';
              }

              final bool isCam = lowerTitle.contains('cam') ||
                  lowerTitle.contains('hdcam') ||
                  lowerTitle.contains('telesync') ||
                  lowerTitle.contains('ts') ||
                  lowerTitle.contains('predvd');

              final bool isHindi = lowerTitle.contains('hindi') ||
                  lowerTitle.contains('hin') ||
                  lowerTitle.contains('dual audio') ||
                  lowerTitle.contains('multi audio') ||
                  rawTitle.contains('हिंदी');

              String size = '';
              final sizeMatch = RegExp(r'💾\s*([\d\.]+\s*(?:GB|MB))', caseSensitive: false)
                  .firstMatch(rawTitle);
              if (sizeMatch != null) {
                size = sizeMatch.group(1) ?? '';
              }

              // Standardized trackers for fast peering
              final trackers = [
                'udp://tracker.opentrackr.org:1337/announce',
                'udp://open.stealth.si:80/announce',
                'udp://tracker.torrent.eu.org:451/announce'
              ].map((tr) => '&tr=${Uri.encodeComponent(tr)}').join();

              final magnet = 'magnet:?xt=urn:btih:$infoHash$trackers';
              final String streamUrl =
                  'https://webtor.io/show?magnet=${Uri.encodeComponent(magnet)}${fileIdx != null ? '&file=$fileIdx' : ''}';

              final cleanTitleLine = rawTitle.split('\n').first;

              results.add(
                TorrentStreamItem(
                  title: cleanTitleLine.isNotEmpty ? cleanTitleLine : name,
                  quality: isCam ? 'CAM-Rip' : quality,
                  size: size,
                  onlineStreamUrl: streamUrl,
                  isHindi: isHindi,
                  isCam: isCam,
                ),
              );
            }
          }
        }
        if (results.isNotEmpty) break;
      } catch (_) {}
    }

    results.sort((a, b) {
      if (a.isHindi && !b.isHindi) return -1;
      if (!a.isHindi && b.isHindi) return 1;
      return 0;
    });

    return results;
  }
}
