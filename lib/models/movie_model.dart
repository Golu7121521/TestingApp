class Movie {
  final int id;
  final String title;
  final String? posterPath;
  final String? backdropPath;
  final String overview;
  final String releaseDate;
  final String mediaType; // 'movie' ya 'tv'

  Movie({
    required this.id,
    required this.title,
    this.posterPath,
    this.backdropPath,
    this.overview = '',
    this.releaseDate = '',
    this.mediaType = 'movie',
  });

  factory Movie.fromJson(Map<String, dynamic> json, {String defaultType = 'movie'}) {
    // Robust ID parsing
    int parsedId = 0;
    if (json['id'] is int) {
      parsedId = json['id'];
    } else if (json['id'] != null) {
      parsedId = int.tryParse(json['id'].toString()) ?? 0;
    }

    // Movies me 'release_date', TV shows me 'first_air_date'
    final rawDate = (json['release_date'] ?? json['first_air_date'] ?? '').toString();
    final parsedYear = rawDate.isNotEmpty ? rawDate.split('-')[0] : '';

    return Movie(
      id: parsedId,
      // Movies me 'title', Web Series me 'name' / 'original_name'
      title: json['title'] ?? json['name'] ?? json['original_name'] ?? 'Untitled',
      posterPath: json['poster_path'],
      backdropPath: json['backdrop_path'],
      overview: json['overview'] ?? '',
      releaseDate: parsedYear,
      mediaType: json['media_type']?.toString() ?? defaultType,
    );
  }

  String get realPosterUrl {
    if (posterPath == null || posterPath!.trim().isEmpty) return '';
    final path = posterPath!.startsWith('/') ? posterPath! : '/$posterPath';
    return 'https://image.tmdb.org/t/p/w500$path';
  }

  String get fullPosterUrl => realPosterUrl;

  String get realBackdropUrl {
    if (backdropPath != null && backdropPath!.trim().isNotEmpty) {
      final path = backdropPath!.startsWith('/') ? backdropPath! : '/$backdropPath';
      return 'https://image.tmdb.org/t/p/w780$path';
    }
    return realPosterUrl;
  }

  String get fullBackdropUrl => realBackdropUrl;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'poster_path': posterPath,
      'backdrop_path': backdropPath,
      'overview': overview,
      'release_date': releaseDate,
      'media_type': mediaType,
    };
  }
}
