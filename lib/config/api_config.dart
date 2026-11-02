class ApiConfig {
  static const String apiKey = "bda15b72d8122a53b19ef34ab5f04523";

  // Official TMDb Base URL
  static const String baseUrl = "https://api.themoviedb.org/3";

  // Multi-CDN Image Loader with Fallbacks for Jio/Airtel/Vi networks
  static String getImageUrl(String? path, {String size = 'w500'}) {
    if (path == null || path.trim().isEmpty) return '';

    final cleanPath = path.startsWith('/') ? path : '/$path';
    final targetUrl = 'https://image.tmdb.org/t/p/$size$cleanPath';

    // Weserv Cache Proxy (Fastest CDN mirror for India)
    return 'https://images.weserv.nl/?url=${Uri.encodeComponent(targetUrl)}&default=${Uri.encodeComponent(targetUrl)}';
  }

  // Direct TMDB fallback in case proxy is bypassed
  static String getDirectImageUrl(String? path, {String size = 'w500'}) {
    if (path == null || path.trim().isEmpty) return '';
    final cleanPath = path.startsWith('/') ? path : '/$path';
    return 'https://image.tmdb.org/t/p/$size$cleanPath';
  }
}
