import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/movie_model.dart';

class TmdbService {
  static const String _key = "bda15b72d8122a53b19ef34ab5f04523";

  static final http.Client _client = http.Client();

  static final Map<String, String> _headers = {
    'User-Agent': 'Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36',
    'Accept': 'application/json',
  };

  static Future<List<Movie>> _fetchList(String endpoint, {String defaultType = 'movie'}) async {
    final urls = [
      'https://api.themoviedb.org/3$endpoint',
      'https://api.tmdb.org/3$endpoint',
    ];

    for (int retry = 0; retry < 3; retry++) {
      for (final u in urls) {
        try {
          final res = await _client.get(Uri.parse(u), headers: _headers).timeout(const Duration(seconds: 10));
          if (res.statusCode == 200) {
            final decoded = json.decode(res.body);
            final data = decoded['results'] as List?;
            if (data != null && data.isNotEmpty) {
              return data
                  .where((m) => m is Map<String, dynamic> && (m['poster_path'] != null || m['backdrop_path'] != null))
                  .map((m) => Movie.fromJson(m, defaultType: m['media_type']?.toString() ?? defaultType))
                  .toList();
            }
          }
        } catch (_) {}
      }
      await Future.delayed(const Duration(milliseconds: 300));
    }
    return <Movie>[];
  }

  static Future<List<Movie>> getTrendingIndia() => _fetchList('/trending/all/day?api_key=$_key&region=IN');
  static Future<List<Movie>> getBollywoodMovies() => _fetchList('/discover/movie?api_key=$_key&with_original_language=hi&sort_by=popularity.desc&region=IN');
  static Future<List<Movie>> getHindiWebSeries() => _fetchList('/discover/tv?api_key=$_key&with_original_language=hi&sort_by=popularity.desc', defaultType: 'tv');
  static Future<List<Movie>> getSouthHindiDubbed() => _fetchList('/discover/movie?api_key=$_key&with_original_language=te|ta|ml|kn&sort_by=popularity.desc&region=IN');
  static Future<List<Movie>> getGlobalHollywoodHindi() => _fetchList('/discover/movie?api_key=$_key&with_original_language=en&region=IN&sort_by=popularity.desc');
  static Future<List<Movie>> getIndianTvSerials() => _fetchList('/discover/tv?api_key=$_key&with_original_language=hi&sort_by=vote_count.desc', defaultType: 'tv');
  static Future<List<Movie>> getActionThrillerShows() => _fetchList('/discover/movie?api_key=$_key&with_genres=28,53&region=IN&sort_by=popularity.desc');
  static Future<List<Movie>> searchMovies(String query) => _fetchList('/search/multi?api_key=$_key&query=${Uri.encodeComponent(query)}&region=IN');
  static Future<List<Movie>> getTrendingTopSearches() => _fetchList('/trending/all/week?api_key=$_key&region=IN');

  // Complete data fetcher (Cast, Crew, Videos, Similar, and External IDs for Scrapers)
  static Future<Map<String, dynamic>> getDetails(int id, String mediaType) async {
    final primaryType = (mediaType == 'tv') ? 'tv' : 'movie';
    final secondaryType = (primaryType == 'tv') ? 'movie' : 'tv';
    final types = [primaryType, secondaryType];

    for (int attempt = 0; attempt < 3; attempt++) {
      for (final type in types) {
        final url = 'https://api.themoviedb.org/3/$type/$id?api_key=$_key&append_to_response=credits,videos,similar,external_ids';
        try {
          final res = await _client.get(Uri.parse(url), headers: _headers).timeout(const Duration(seconds: 10));
          if (res.statusCode == 200) {
            final data = json.decode(res.body);
            if (data is Map<String, dynamic> && data.isNotEmpty) {
              data['resolved_media_type'] = type;
              return data;
            }
          }
        } catch (_) {}
      }
      await Future.delayed(const Duration(milliseconds: 300));
    }
    return {};
  }

  // Episodes by Season Number
  static Future<List<Map<String, dynamic>>> getSeasonEpisodes(int seriesId, int seasonNumber) async {
    final url = 'https://api.themoviedb.org/3/tv/$seriesId/season/$seasonNumber?api_key=$_key';
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        final res = await _client.get(Uri.parse(url), headers: _headers).timeout(const Duration(seconds: 10));
        if (res.statusCode == 200) {
          final data = json.decode(res.body);
          if (data['episodes'] != null) {
            return List<Map<String, dynamic>>.from(data['episodes']);
          }
        }
      } catch (_) {}
      await Future.delayed(const Duration(milliseconds: 300));
    }
    return [];
  }
}
