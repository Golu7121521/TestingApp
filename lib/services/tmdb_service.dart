import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/movie_model.dart';

class TorrentStreamItem {
  final String title;
  final String quality;
  final String size;
  final int seeders;
  final String infoHash;
  final int fileIdx;
  final bool isHindi;
  final String audioLanguage;

  TorrentStreamItem({
    required this.title,
    required this.quality,
    required this.size,
    required this.seeders,
    required this.infoHash,
    this.fileIdx = 0,
    required this.isHindi,
    required this.audioLanguage,
  });
}

class TorrentioService {
  static const String _tmdbApiKey = 'bda15b72d8122a53b19ef34ab5f04523';

  static final Map<String, String> _headers = {
    'User-Agent': 'Mozilla/5.0 (Linux; Android 10; Redmi 9 Prime) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36',
    'Accept': 'application/json',
  };

  static Future<String?> getImdbId(int tmdbId, String mediaType, {String? movieTitle}) async {
    final type = (mediaType.isEmpty || mediaType == 'movie') ? 'movie' : 'tv';
    try {
      final url = Uri.parse('https://api.themoviedb.org/3/$type/$tmdbId/external_ids?api_key=$_tmdbApiKey');
      final res = await http.get(url, headers: _headers).timeout(const Duration(seconds: 8));
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
    final idParam = isTV ? 'series/$imdbId:$season:$episode.json' : 'movie/$imdbId.json';

    final endpoints = [
      'https://torrentio.strem.fun/stream/$idParam',
      'https://torrentio.herokuapp.com/stream/$idParam',
      'https://mediafusion.elfhosted.com/stream/$idParam',
    ];

    for (final ep in endpoints) {
      try {
        final res = await http.get(Uri.parse(ep), headers: _headers).timeout(const Duration(seconds: 8));
        if (res.statusCode == 200) {
          final data = json.decode(res.body);
          final streams = data['streams'] as List? ?? [];

          for (var s in streams) {
            final titleRaw = (s['title'] ?? '').toString();
            final infoHash = (s['infoHash'] ?? '').toString();
            if (infoHash.isEmpty) continue;

            String quality = 'HD';
            if (titleRaw.contains('2160p') || titleRaw.contains('4K')) {
              quality = '4K Ultra HD';
            } else if (titleRaw.contains('1080p')) {
              quality = '1080p Full HD';
            } else if (titleRaw.contains('720p')) {
              quality = '720p HD';
            } else if (titleRaw.contains('480p')) {
              quality = '480p SD';
            }

            final sizeMatch = RegExp(r'💾\s*([\d\.]+\s*(?:GB|MB))', caseSensitive: false).firstMatch(titleRaw);
            final size = sizeMatch != null ? sizeMatch.group(1)! : '1.2 GB';

            final seedMatch = RegExp(r'👤\s*(\d+)').firstMatch(titleRaw);
            final seeders = seedMatch != null ? int.tryParse(seedMatch.group(1)!) ?? 10 : 10;

            final lower = titleRaw.toLowerCase();
            final isHindi = lower.contains('hindi') || lower.contains('dual') || lower.contains('multi');

            String audioLang = 'English';
            if (lower.contains('hindi') && (lower.contains('tamil') || lower.contains('telugu'))) {
              audioLang = 'Hindi + Multi Audio';
            } else if (lower.contains('hindi')) {
              audioLang = 'Hindi Dubbed 🇮🇳';
            } else if (lower.contains('dual')) {
              audioLang = 'Dual Audio [Hindi+Eng]';
            }

            final fileIdx = (s['fileIdx'] is int) ? s['fileIdx'] as int : 0;

            if (!results.any((r) => r.infoHash == infoHash)) {
              results.add(TorrentStreamItem(
                title: titleRaw.split('\n').first.replaceAll(RegExp(r'[^\w\s\.\-]'), '').trim(),
                quality: quality,
                size: size,
                seeders: seeders,
                infoHash: infoHash,
                fileIdx: fileIdx,
                isHindi: isHindi,
                audioLanguage: audioLang,
              ));
            }
          }
          if (results.isNotEmpty) break;
        }
      } catch (_) {}
    }

    results.sort((a, b) {
      if (a.isHindi && !b.isHindi) return -1;
      if (!a.isHindi && b.isHindi) return 1;
      return b.seeders.compareTo(a.seeders);
    });

    return results;
  }

  // 1DM Tier-1 Working Trackers (UDP + HTTP + HTTPS + WebTorrent)
  static String buildMagnetUri(String infoHash, String title) {
    final encodedTitle = Uri.encodeComponent(title);
    return 'magnet:?xt=urn:btih:$infoHash&dn=$encodedTitle'
        '&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce'
        '&tr=http%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce'
        '&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce'
        '&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce'
        '&tr=https%3A%2F%2Ftracker.tamersunion.org%3A443%2Fannounce'
        '&tr=udp%3A%2F%2Fexplodie.org%3A6969%2Fannounce'
        '&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce'
        '&tr=udp%3A%2F%2F9.rarbg.to%3A2920%2Fannounce'
        '&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce'
        '&tr=http%3A%2F%2Ftracker.openbittorrent.com%3A80%2Fannounce'
        '&tr=wss%3A%2F%2Ftracker.openwebtorrent.com'
        '&tr=wss%3A%2F%2Ftracker.btorrent.xyz';
  }
}

class TmdbService {
  static const String apiKey = 'bda15b72d8122a53b19ef34ab5f04523';
  static const String baseUrl = 'https://api.themoviedb.org/3';

  static final Map<String, String> _networkHeaders = {
    'User-Agent': 'Mozilla/5.0 (Linux; Android 10; Redmi 9 Prime) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
    'Accept': 'application/json',
  };

  static String formatPoster(String? rawPath) {
    if (rawPath == null || rawPath.isEmpty) return '';
    if (rawPath.startsWith('http')) return rawPath;
    final cleanPath = rawPath.startsWith('/') ? rawPath : '/$rawPath';
    return 'https://image.tmdb.org/t/p/w500$cleanPath';
  }

  static Future<List<Movie>> _fetchList(String endpoint) async {
    final connector = endpoint.contains('?') ? '&' : '?';
    final requestUri = Uri.parse('$baseUrl$endpoint${connector}api_key=$apiKey');

    try {
      final res = await http.get(requestUri, headers: _networkHeaders).timeout(const Duration(seconds: 12));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        final list = (data['results'] as List? ?? []);
        return list
            .map((m) {
              final map = Map<String, dynamic>.from(m);
              if (map['poster_path'] != null) {
                map['poster_path'] = formatPoster(map['poster_path'].toString());
              }
              return Movie.fromJson(map);
            })
            .where((m) => m.posterPath != null && m.posterPath!.isNotEmpty)
            .toList();
      }
    } catch (_) {}
    return [];
  }

  static Future<List<Movie>> getTrendingIndia() async => _fetchList('/trending/movie/day?region=IN');
  static Future<List<Movie>> getHindiWebSeries() async => _fetchList('/discover/tv?with_original_language=hi&without_genres=10766&sort_by=popularity.desc');
  static Future<List<Movie>> getSouthHindiDubbed() async => _fetchList('/discover/movie?with_original_language=te|ta|ml|kn&sort_by=popularity.desc');
  static Future<List<Movie>> getBollywoodMovies() async => _fetchList('/discover/movie?with_original_language=hi&sort_by=popularity.desc');
  static Future<List<Movie>> getGlobalHollywoodHindi() async => _fetchList('/discover/movie?with_original_language=en&sort_by=popularity.desc');
  static Future<List<Movie>> getIndianTvSerials() async => _fetchList('/discover/tv?with_original_language=hi&with_genres=18,80&without_genres=10766&sort_by=popularity.desc');
  static Future<List<Movie>> getActionThrillerShows() async => _fetchList('/discover/tv?with_genres=10759&without_genres=10766&sort_by=popularity.desc');
  static Future<List<Movie>> getTrendingTopSearches() async => _fetchList('/trending/movie/week?region=IN');

  static Future<List<Movie>> searchMovies(String query) async {
    final clean = query.trim();
    if (clean.isEmpty) return [];

    try {
      final res = await http.get(
        Uri.parse('$baseUrl/search/multi?query=${Uri.encodeComponent(clean)}&include_adult=false&api_key=$apiKey'),
        headers: _networkHeaders,
      ).timeout(const Duration(seconds: 10));

      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        final list = (data['results'] as List? ?? []);
        return list
            .where((m) => m['media_type'] == 'movie' || m['media_type'] == 'tv')
            .map((m) {
              final map = Map<String, dynamic>.from(m);
              if (map['poster_path'] != null) {
                map['poster_path'] = formatPoster(map['poster_path'].toString());
              }
              return Movie.fromJson(map);
            })
            .where((m) => m.posterPath != null && m.posterPath!.isNotEmpty)
            .toList();
      }
    } catch (_) {}
    return [];
  }

  static Future<Map<String, dynamic>> getDetails(int id, String mediaType) async {
    final type = (mediaType.isEmpty || mediaType == 'movie') ? 'movie' : 'tv';
    final targetUri = Uri.parse('$baseUrl/$type/$id?append_to_response=credits,similar,videos&api_key=$apiKey');

    try {
      final res = await http.get(targetUri, headers: _networkHeaders).timeout(const Duration(seconds: 12));
      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        data['resolved_media_type'] = type;
        return data;
      }
    } catch (_) {}
    return {};
  }

  static Future<List<Map<String, dynamic>>> getSeasonEpisodes(int tvId, int seasonNum) async {
    try {
      final res = await http.get(
        Uri.parse('$baseUrl/tv/$tvId/season/$seasonNum?api_key=$apiKey'),
        headers: _networkHeaders,
      ).timeout(const Duration(seconds: 10));

      if (res.statusCode == 200) {
        final data = json.decode(res.body);
        return List<Map<String, dynamic>>.from(data['episodes'] ?? []);
      }
    } catch (_) {}
    return [];
  }
}
