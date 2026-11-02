import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/movie_model.dart';

class TorrentStreamItem {
  final String title;
  final String quality;
  final String size;
  final int seeders;
  final String infoHash;
  final bool isHindi;

  TorrentStreamItem({
    required this.title,
    required this.quality,
    required this.size,
    required this.seeders,
    required this.infoHash,
    required this.isHindi,
  });
}

class TorrentioService {
  // Asli TMDB API key yahan dalein
  static const String _tmdbApiKey = 'YOUR_ACTUAL_TMDB_KEY_HERE';

  static Future<String?> getImdbId(int tmdbId, String mediaType, {String? movieTitle}) async {
    try {
      final url = Uri.parse('https://api.themoviedb.org/3/$mediaType/$tmdbId/external_ids?api_key=$_tmdbApiKey');
      final res = await http.get(url).timeout(const Duration(seconds: 8));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        return data['imdb_id'] as String?;
      }
    } catch (_) {}
    return null;
  }

  static Future<List<TorrentStreamItem>> getTorrentStreams({
    required String imdbId,
    required bool isTV,
    int season = 1,
    int episode = 1,
  }) async {
    final List<TorrentStreamItem> results = [];
    final endpoint = isTV
        ? 'https://torrentio.strem.fun/stream/series/$imdbId:$season:$episode.json'
        : 'https://torrentio.strem.fun/stream/movie/$imdbId.json';

    try {
      final res = await http.get(Uri.parse(endpoint)).timeout(const Duration(seconds: 10));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        final streams = data['streams'] as List? ?? [];

        for (var s in streams) {
          final titleRaw = (s['title'] ?? '').toString();
          final infoHash = (s['infoHash'] ?? '').toString();
          if (infoHash.isEmpty) continue;

          String quality = 'HD';
          if (titleRaw.contains('2160p') || titleRaw.contains('4K')) {
            quality = '4K';
          } else if (titleRaw.contains('1080p')) {
            quality = '1080p';
          } else if (titleRaw.contains('720p')) {
            quality = '720p';
          } else if (titleRaw.contains('480p')) {
            quality = '480p';
          }

          final sizeMatch = RegExp(r'💾\s*([\d\.]+\s*(?:GB|MB))', caseSensitive: false).firstMatch(titleRaw);
          final size = sizeMatch != null ? sizeMatch.group(1)! : '1.2 GB';

          final seedMatch = RegExp(r'👤\s*(\d+)').firstMatch(titleRaw);
          final seeders = seedMatch != null ? int.tryParse(seedMatch.group(1)!) ?? 5 : 5;

          final isHindi = titleRaw.toLowerCase().contains('hindi') ||
              titleRaw.toLowerCase().contains('dual') ||
              titleRaw.toLowerCase().contains('multi');

          results.add(TorrentStreamItem(
            title: titleRaw.split('\n').first.replaceAll(RegExp(r'[^\w\s\.\-]'), '').trim(),
            quality: quality,
            size: size,
            seeders: seeders,
            infoHash: infoHash,
            isHindi: isHindi,
          ));
        }
      }
    } catch (_) {}
    results.sort((a, b) => b.seeders.compareTo(a.seeders));
    return results;
  }

  static Future<List<TorrentStreamItem>> fetchStreams({
    required String imdbId,
    required bool isTV,
    int season = 1,
    int episode = 1,
  }) async {
    return getTorrentStreams(imdbId: imdbId, isTV: isTV, season: season, episode: episode);
  }

  static String buildMagnetUri(String infoHash, String title) {
    final encodedTitle = Uri.encodeComponent(title);
    return 'magnet:?xt=urn:btih:$infoHash&dn=$encodedTitle&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce';
  }
}

class TmdbService {
  // Yahan apni TMDB API Key dalein
  static const String apiKey = 'YOUR_ACTUAL_TMDB_KEY_HERE';
  static const String baseUrl = 'https://api.themoviedb.org/3';

  // Fallback public catalog (bina kisi API key ke kaam karega)
  static const String publicCatalogUrl = 'https://v3-cinemeta.strem.io/catalog/movie/top.json';

  static Future<List<Movie>> _fetchList(String endpoint) async {
    // Agar API Key nahi dali hai toh seedha public catalog fetch karein
    if (apiKey == 'YOUR_TMDB_API_KEY' || apiKey.isEmpty) {
      return _fetchPublicCatalog();
    }

    try {
      final res = await http.get(Uri.parse('$baseUrl$endpoint&api_key=$apiKey'));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        final list = (data['results'] as List? ?? []);
        return list.map((m) => Movie.fromJson(m)).where((m) => m.posterPath != null).toList();
      }
    } catch (_) {}
    return _fetchPublicCatalog();
  }

  static Future<List<Movie>> _fetchPublicCatalog() async {
    try {
      final res = await http.get(Uri.parse(publicCatalogUrl));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        final metas = data['metas'] as List? ?? [];
        return metas.map((m) => Movie(
          id: int.tryParse(m['imdb_id']?.replaceAll(RegExp(r'\D'), '') ?? '0') ?? 0,
          title: m['name'] ?? '',
          posterPath: m['poster'] ?? '',
          overview: m['description'] ?? '',
          releaseDate: m['releaseInfo'] ?? '',
          voteAverage: (m['imdbRating'] != null) ? double.tryParse(m['imdbRating'].toString()) ?? 7.0 : 7.0,
        )).toList();
      }
    } catch (_) {}
    return [];
  }

  static Future<List<Movie>> getTrendingIndia() async =>
      _fetchList('/trending/all/day?region=IN');

  static Future<List<Movie>> getHindiWebSeries() async =>
      _fetchList('/discover/tv?with_original_language=hi&sort_by=popularity.desc');

  static Future<List<Movie>> getSouthHindiDubbed() async =>
      _fetchList('/discover/movie?with_original_language=te|ta|ml|kn&sort_by=popularity.desc');

  static Future<List<Movie>> getBollywoodMovies() async =>
      _fetchList('/discover/movie?with_original_language=hi&sort_by=popularity.desc');

  static Future<List<Movie>> getGlobalHollywoodHindi() async =>
      _fetchList('/discover/movie?with_original_language=en&sort_by=popularity.desc');

  static Future<List<Movie>> getIndianTvSerials() async =>
      _fetchList('/discover/tv?with_genres=10766&with_original_language=hi');

  static Future<List<Movie>> getActionThrillerShows() async =>
      _fetchList('/discover/tv?with_genres=10759&sort_by=popularity.desc');

  static Future<List<Movie>> getTrendingTopSearches() async =>
      _fetchList('/trending/all/week?region=IN');

  static Future<List<Movie>> searchMovies(String query) async {
    try {
      final res = await http.get(Uri.parse('$baseUrl/search/multi?query=${Uri.encodeComponent(query)}&api_key=$apiKey'));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        final list = (data['results'] as List? ?? []);
        return list.map((m) => Movie.fromJson(m)).where((m) => m.posterPath != null).toList();
      }
    } catch (_) {}
    return [];
  }

  static Future<Map<String, dynamic>> getDetails(int id, String mediaType) async {
    try {
      final res = await http.get(Uri.parse('$baseUrl/$mediaType/$id?append_to_response=credits,similar,videos&api_key=$apiKey'));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        data['resolved_media_type'] = mediaType;
        return data;
      }
    } catch (_) {}
    return {};
  }

  static Future<List<Map<String, dynamic>>> getSeasonEpisodes(int tvId, int seasonNum) async {
    try {
      final res = await http.get(Uri.parse('$baseUrl/tv/$tvId/season/$seasonNum?api_key=$apiKey'));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        return List<Map<String, dynamic>>.from(data['episodes'] ?? []);
      }
    } catch (_) {}
    return [];
  }
}
