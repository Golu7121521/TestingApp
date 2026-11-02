import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/movie_model.dart';
import '../services/tmdb_service.dart';
import '../services/cloudstream_extractor.dart';
import '../services/torrentio_service.dart';
import 'trailer_player_screen.dart';
import 'exo_player_screen.dart';
import 'all_episodes_screen.dart';

class MovieDetailScreen extends StatefulWidget {
  final int movieId;
  final String mediaType;
  final Function(Movie) onAddToList;

  const MovieDetailScreen({
    super.key,
    required this.movieId,
    required this.mediaType,
    required this.onAddToList,
  });

  @override
  State<MovieDetailScreen> createState() => _MovieDetailScreenState();
}

class _MovieDetailScreenState extends State<MovieDetailScreen> {
  Map<String, dynamic>? data;
  bool isLoading = true;
  bool isOverviewExpanded = false;

  int selectedSeason = 1;
  List<Map<String, dynamic>> episodesList = [];
  bool isLoadingEpisodes = false;

  @override
  void initState() {
    super.initState();
    loadMovieDetails();
  }

  Future<void> loadMovieDetails() async {
    setState(() => isLoading = true);
    final res = await TmdbService.getDetails(widget.movieId, widget.mediaType);
    if (mounted) {
      setState(() {
        data = res;
        isLoading = false;
      });

      if (res['resolved_media_type'] == 'tv') {
        loadEpisodes(1);
      }
    }
  }

  Future<void> loadEpisodes(int seasonNum) async {
    setState(() {
      selectedSeason = seasonNum;
      isLoadingEpisodes = true;
    });

    final eps = await TmdbService.getSeasonEpisodes(widget.movieId, seasonNum);
    if (mounted) {
      setState(() {
        episodesList = eps;
        isLoadingEpisodes = false;
      });
    }
  }

  bool isReleased() {
    if (data == null) return false;
    final releaseDateStr = (data!['release_date'] ?? data!['first_air_date'] ?? '').toString();
    if (releaseDateStr.isEmpty) return true;

    try {
      final releaseDate = DateTime.parse(releaseDateStr);
      return !releaseDate.isAfter(DateTime.now());
    } catch (_) {
      return true;
    }
  }

  Future<void> openStreamPlayer({int season = 1, int episode = 1}) async {
    final resolvedType = data!['resolved_media_type'] ?? widget.mediaType;
    final title = (data!['title'] ?? data!['name'] ?? 'Playing').toString();
    final releaseDate = (data!['release_date'] ?? data!['first_air_date'] ?? '').toString();
    final year = releaseDate.isNotEmpty ? releaseDate.split('-')[0] : '2024';
    final isTV = resolvedType == 'tv';

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: CircularProgressIndicator(color: Color(0xFFE50914)),
      ),
    );

    String streamUrl = '';
    final queryTitle = isTV ? '$title S${season}E$episode' : title;

    try {
      final directStreams = await CloudStreamExtractor.fetchDirectHindiStreams(
        movieTitle: queryTitle,
        year: year,
      );

      if (directStreams.isNotEmpty) {
        streamUrl = directStreams.first.videoUrl;
      } else {
        final imdbId = await TorrentioService.getImdbId(
          widget.movieId,
          resolvedType,
          movieTitle: title,
        );

        if (imdbId != null && imdbId.isNotEmpty) {
          final torrentStreams = await TorrentioService.fetchStreams(
            imdbId: imdbId,
            isTV: isTV,
            season: season,
            episode: episode,
          );
          if (torrentStreams.isNotEmpty) {
            streamUrl = torrentStreams.first.onlineStreamUrl;
          }
        }
      }
    } catch (_) {}

    if (mounted) Navigator.pop(context);

    if (streamUrl.isEmpty) {
      streamUrl = isTV
          ? 'https://vidsrc.me/embed/tv?tmdb=${widget.movieId}&season=$season&episode=$episode'
          : 'https://vidsrc.me/embed/movie?tmdb=${widget.movieId}';
    }

    if (mounted) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => ExoPlayerScreen(
            videoUrl: streamUrl,
            title: isTV ? '$title - S${season}E$episode' : title,
          ),
        ),
      );
    }
  }

  void openAllEpisodesPage() {
    final title = (data!['title'] ?? data!['name'] ?? 'Episodes').toString();
    final totalSeasons = (data!['number_of_seasons'] as int?) ?? 1;

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => AllEpisodesScreen(
          seriesId: widget.movieId,
          seriesTitle: title,
          totalSeasons: totalSeasons,
          initialSeason: selectedSeason,
        ),
      ),
    );
  }

  void playTrailer(String key, List<dynamic> videos) {
    if (key.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Trailer not available.')),
      );
      return;
    }

    final title = (data!['title'] ?? data!['name'] ?? 'Trailer').toString();
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TrailerPlayerScreen(
          initialVideoKey: key,
          title: title,
          allVideos: videos,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Scaffold(
        backgroundColor: Color(0xFF0F0F0F),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              CircularProgressIndicator(color: Color(0xFFE50914)),
              SizedBox(height: 14),
              Text('Fetching movie details...', style: TextStyle(color: Colors.white54, fontSize: 13)),
            ],
          ),
        ),
      );
    }

    if (data == null || data!.isEmpty) {
      return Scaffold(
        backgroundColor: const Color(0xFF0F0F0F),
        appBar: AppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back, color: Colors.white),
            onPressed: () => Navigator.pop(context),
          ),
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.wifi_off_rounded, color: Colors.white38, size: 54),
                const SizedBox(height: 14),
                const Text('Network Timeout', style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 6),
                const Text('Unable to connect to TMDb servers. Check connection.', style: TextStyle(color: Colors.white54, fontSize: 12), textAlign: TextAlign.center),
                const SizedBox(height: 20),
                SizedBox(
                  width: 140,
                  height: 42,
                  child: ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFE50914),
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                    ),
                    icon: const Icon(Icons.refresh, size: 18),
                    label: const Text('Try Again', style: TextStyle(fontWeight: FontWeight.bold)),
                    onPressed: loadMovieDetails,
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final resolvedType = data!['resolved_media_type'] ?? widget.mediaType;
    final isTV = resolvedType == 'tv';

    final title = (data!['title'] ?? data!['name'] ?? 'Untitled').toString();
    final backdropPath = data!['backdrop_path'] ?? data!['poster_path'];
    final releaseDate = (data!['release_date'] ?? data!['first_air_date'] ?? '').toString();
    final year = releaseDate.isNotEmpty ? releaseDate.split('-')[0] : '2024';

    String duration = '';
    if (!isTV && data!['runtime'] != null && data!['runtime'] > 0) {
      final runtime = data!['runtime'] as int;
      duration = '${runtime ~/ 60}h ${runtime % 60}m';
    } else if (isTV && data!['number_of_seasons'] != null) {
      duration = '${data!['number_of_seasons']} Season${data!['number_of_seasons'] > 1 ? 's' : ''}';
    }

    final genresList = (data!['genres'] as List?)?.map((g) => g['name'].toString()).toList() ?? [];
    final genresText = genresList.take(3).join(' • ');

    final voteAverage = (data!['vote_average'] as num?)?.toDouble() ?? 0.0;
    final voteCount = (data!['vote_count'] as int?) ?? 0;
    final ratingScore = voteAverage > 0 ? voteAverage.toStringAsFixed(1) : '7.5';
    final tomatometer = ((voteAverage * 10).clamp(50, 99)).toInt();
    final audienceScore = ((voteAverage * 10 + 4).clamp(55, 98)).toInt();

    final credits = data!['credits'] ?? {};
    final crew = (credits['crew'] as List?) ?? [];
    final cast = (credits['cast'] as List?) ?? [];

    String director = 'N/A';
    List<String> writers = [];

    for (var c in crew) {
      if (c['job'] == 'Director') {
        director = c['name'] ?? 'N/A';
      } else if (c['job'] == 'Writer' || c['job'] == 'Screenplay' || c['department'] == 'Writing') {
        if (!writers.contains(c['name'])) writers.add(c['name'].toString());
      }
    }
    if (isTV && data!['created_by'] != null && (data!['created_by'] as List).isNotEmpty) {
      director = (data!['created_by'] as List).map((c) => c['name']).join(', ');
    }

    final stars = cast.take(4).map((c) => c['name'].toString()).join(', ');

    final similar = ((data!['similar']?['results'] as List?) ?? [])
        .map((m) => Movie.fromJson(m, defaultType: resolvedType))
        .where((m) => m.posterPath != null && m.posterPath!.isNotEmpty)
        .toList();

    final videos = ((data!['videos']?['results'] as List?) ?? [])
        .where((v) => v['site'] == 'YouTube')
        .toList();

    String mainTrailerKey = '';
    if (videos.isNotEmpty) {
      final officialTrailer = videos.firstWhere(
        (v) => (v['type'] == 'Trailer'),
        orElse: () => videos.first,
      );
      mainTrailerKey = officialTrailer['key'] ?? '';
    }

    final bool released = isReleased();
    final totalSeasons = (data!['number_of_seasons'] as int?) ?? 1;

    final displayEpisodes = episodesList.take(7).toList();
    final hasMoreEpisodes = episodesList.length > 7 || totalSeasons > 1;

    return Scaffold(
      backgroundColor: const Color(0xFF0F0F0F),
      body: Stack(
        children: [
          SingleChildScrollView(
            padding: EdgeInsets.zero,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Stack(
                  alignment: Alignment.bottomCenter,
                  children: [
                    if (backdropPath != null)
                      CachedNetworkImage(
                        imageUrl: 'https://image.tmdb.org/t/p/w780$backdropPath',
                        height: 440,
                        width: double.infinity,
                        fit: BoxFit.cover,
                        alignment: Alignment.topCenter,
                        placeholder: (_, __) => Container(height: 440, color: const Color(0xFF1E1E1E)),
                        errorWidget: (_, __, ___) => Container(height: 440, color: const Color(0xFF1E1E1E)),
                      )
                    else
                      Container(height: 440, color: const Color(0xFF1E1E1E)),

                    Container(
                      height: 440,
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          stops: const [0.2, 0.65, 1.0],
                          colors: [
                            Colors.transparent,
                            const Color(0xFF0F0F0F).withOpacity(0.7),
                            const Color(0xFF0F0F0F),
                          ],
                        ),
                      ),
                    ),

                    if (mainTrailerKey.isNotEmpty)
                      Positioned(
                        right: 16,
                        bottom: 80,
                        child: InkWell(
                          onTap: () => playTrailer(mainTrailerKey, videos),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                            decoration: BoxDecoration(
                              color: Colors.black.withOpacity(0.75),
                              borderRadius: BorderRadius.circular(20),
                              border: Border.all(color: Colors.white38),
                            ),
                            child: const Row(
                              children: [
                                Icon(Icons.play_circle_fill, color: Colors.white, size: 20),
                                SizedBox(width: 6),
                                Text('Watch Trailer', style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold)),
                              ],
                            ),
                          ),
                        ),
                      ),

                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      child: Column(
                        children: [
                          Text(
                            title.toUpperCase(),
                            textAlign: TextAlign.center,
                            style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w900, letterSpacing: 2.0, color: Colors.white),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            [year, if (genresText.isNotEmpty) genresText, if (duration.isNotEmpty) duration, 'U/A 13+'].join(' • '),
                            textAlign: TextAlign.center,
                            style: const TextStyle(color: Colors.white60, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),

                // WATCH NOW BUTTON
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: released ? const Color(0xFFE50914) : const Color(0xFF262626),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                          ),
                          icon: Icon(released ? Icons.play_arrow : Icons.lock_clock, size: 22),
                          label: Text(
                            released ? 'Watch Now' : 'Unreleased',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                          ),
                          onPressed: () {
                            if (released) {
                              openStreamPlayer();
                            } else {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text('$title is not yet released. Coming Soon!')),
                              );
                            }
                          },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: OutlinedButton.icon(
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: Colors.white30),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                          ),
                          icon: const Icon(Icons.add, size: 20),
                          label: const Text('Add to My List', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                          onPressed: () {
                            widget.onAddToList(Movie.fromJson(data!, defaultType: resolvedType));
                          },
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // EPISODES GRID
                if (isTV) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text('Episodes', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold, color: Colors.white)),
                            InkWell(
                              onTap: openAllEpisodesPage,
                              child: const Text('See All Episodes >', style: TextStyle(color: Color(0xFFE50914), fontSize: 12, fontWeight: FontWeight.bold)),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        if (isLoadingEpisodes)
                          const Center(
                            child: Padding(
                              padding: EdgeInsets.all(20.0),
                              child: CircularProgressIndicator(color: Color(0xFFE50914)),
                            ),
                          )
                        else
                          GridView.builder(
                            shrinkWrap: true,
                            physics: const NeverScrollableScrollPhysics(),
                            itemCount: hasMoreEpisodes ? displayEpisodes.length + 1 : displayEpisodes.length,
                            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                              crossAxisCount: 2,
                              childAspectRatio: 1.45,
                              crossAxisSpacing: 10,
                              mainAxisSpacing: 10,
                            ),
                            itemBuilder: (context, idx) {
                              if (idx == displayEpisodes.length && hasMoreEpisodes) {
                                return InkWell(
                                  onTap: openAllEpisodesPage,
                                  child: Container(
                                    decoration: BoxDecoration(
                                      color: const Color(0xFF1F1F1F),
                                      borderRadius: BorderRadius.circular(8),
                                      border: Border.all(color: const Color(0xFFE50914).withOpacity(0.5)),
                                    ),
                                    child: const Column(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        Icon(Icons.video_library_rounded, color: Color(0xFFE50914), size: 28),
                                        SizedBox(height: 6),
                                        Text('More Episodes', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12)),
                                        Text('View All Seasons', style: TextStyle(color: Colors.white54, fontSize: 10)),
                                      ],
                                    ),
                                  ),
                                );
                              }

                              final ep = displayEpisodes[idx];
                              final stillPath = ep['still_path'];
                              final epNum = ep['episode_number'] ?? (idx + 1);

                              return InkWell(
                                onTap: () => openStreamPlayer(season: selectedSeason, episode: epNum),
                                child: ClipRRect(
                                  borderRadius: BorderRadius.circular(8),
                                  child: Stack(
                                    fit: StackFit.expand,
                                    children: [
                                      stillPath != null
                                          ? CachedNetworkImage(
                                              imageUrl: 'https://image.tmdb.org/t/p/w300$stillPath',
                                              fit: BoxFit.cover,
                                              placeholder: (_, __) => Container(color: const Color(0xFF1E1E1E)),
                                              errorWidget: (_, __, ___) => Container(color: const Color(0xFF1E1E1E)),
                                            )
                                          : Container(color: const Color(0xFF1E1E1E)),
                                      Container(
                                        decoration: BoxDecoration(
                                          gradient: LinearGradient(
                                            begin: Alignment.topCenter,
                                            end: Alignment.bottomCenter,
                                            colors: [Colors.transparent, Colors.black.withOpacity(0.8)],
                                          ),
                                        ),
                                      ),
                                      const Center(
                                        child: CircleAvatar(
                                          radius: 13,
                                          backgroundColor: Colors.black54,
                                          child: Icon(Icons.play_arrow, color: Colors.white, size: 16),
                                        ),
                                      ),
                                      Positioned(
                                        bottom: 6,
                                        left: 8,
                                        right: 8,
                                        child: Text(
                                          'EP $epNum • ${ep['name'] ?? ''}',
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              );
                            },
                          ),
                        const SizedBox(height: 20),
                      ],
                    ),
                  ),
                ],

                // RATINGS CARD
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
                    decoration: BoxDecoration(color: const Color(0xFF161616), borderRadius: BorderRadius.circular(8)),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                              decoration: BoxDecoration(color: const Color(0xFFF5C518), borderRadius: BorderRadius.circular(3)),
                              child: const Text('IMDb', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold, fontSize: 11)),
                            ),
                            const SizedBox(width: 8),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('$ratingScore/10', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 13)),
                                Text('${(voteCount / 1000).toStringAsFixed(1)}K votes', style: const TextStyle(color: Colors.white54, fontSize: 10)),
                              ],
                            ),
                          ],
                        ),
                        Container(height: 25, width: 1, color: Colors.white12),
                        Row(
                          children: [
                            const Text('🍅', style: TextStyle(fontSize: 16)),
                            const SizedBox(width: 6),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('$tomatometer%', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 13)),
                                const Text('Tomatometer', style: TextStyle(color: Colors.white54, fontSize: 10)),
                              ],
                            ),
                          ],
                        ),
                        Container(height: 25, width: 1, color: Colors.white12),
                        Row(
                          children: [
                            const Text('🍿', style: TextStyle(fontSize: 16)),
                            const SizedBox(width: 6),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('$audienceScore%', style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 13)),
                                const Text('Audience', style: TextStyle(color: Colors.white54, fontSize: 10)),
                              ],
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 18),

                // OVERVIEW
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        data!['overview'] != null && data!['overview'].toString().isNotEmpty
                            ? data!['overview']
                            : 'No synopsis available for this title.',
                        maxLines: isOverviewExpanded ? 15 : 3,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Colors.white70, fontSize: 13, height: 1.4),
                      ),
                      if (data!['overview'] != null && data!['overview'].toString().length > 120) ...[
                        const SizedBox(height: 4),
                        InkWell(
                          onTap: () => setState(() => isOverviewExpanded = !isOverviewExpanded),
                          child: Text(
                            isOverviewExpanded ? 'Read Less ⌃' : 'Read More ⌄',
                            style: const TextStyle(color: Color(0xFFE50914), fontWeight: FontWeight.bold, fontSize: 12),
                          ),
                        ),
                      ]
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // CREW
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Column(
                    children: [
                      _buildInfoRow('Director', director),
                      if (writers.isNotEmpty) _buildInfoRow('Writers', writers.take(3).join(', ')),
                      if (stars.isNotEmpty) _buildInfoRow('Stars', stars),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // TRAILERS
                if (videos.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: const [
                        Text('Trailers & Clips', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white)),
                        Text('See All >', style: TextStyle(color: Colors.white54, fontSize: 12)),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 110,
                    child: ListView.separated(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      scrollDirection: Axis.horizontal,
                      itemCount: videos.take(6).length,
                      separatorBuilder: (_, __) => const SizedBox(width: 12),
                      itemBuilder: (context, i) {
                        final v = videos[i];
                        final key = v['key'] ?? '';
                        return InkWell(
                          onTap: () => playTrailer(key, videos),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Stack(
                              alignment: Alignment.center,
                              children: [
                                CachedNetworkImage(
                                  imageUrl: 'https://img.youtube.com/vi/$key/mqdefault.jpg',
                                  width: 170,
                                  height: 105,
                                  fit: BoxFit.cover,
                                  placeholder: (_, __) => Container(width: 170, height: 105, color: const Color(0xFF1E1E1E)),
                                  errorWidget: (_, __, ___) => Container(width: 170, height: 105, color: const Color(0xFF1E1E1E)),
                                ),
                                Container(width: 170, height: 105, color: Colors.black38),
                                const Icon(Icons.play_circle_fill, color: Colors.white, size: 36),
                                Positioned(
                                  bottom: 6,
                                  left: 8,
                                  right: 8,
                                  child: Text(
                                    v['name'] ?? 'Trailer',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 22),
                ],

                // SIMILAR
                if (similar.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('Similar ${isTV ? "Series" : "Movies"}', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white)),
                        const Text('See All >', style: TextStyle(color: Colors.white54, fontSize: 12)),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 170,
                    child: ListView.separated(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      scrollDirection: Axis.horizontal,
                      itemCount: similar.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 10),
                      itemBuilder: (context, i) {
                        final sim = similar[i];
                        return InkWell(
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => MovieDetailScreen(
                                  movieId: sim.id,
                                  mediaType: sim.mediaType,
                                  onAddToList: widget.onAddToList,
                                ),
                              ),
                            );
                          },
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              ClipRRect(
                                borderRadius: BorderRadius.circular(6),
                                child: CachedNetworkImage(
                                  imageUrl: 'https://image.tmdb.org/t/p/w342${sim.posterPath}',
                                  width: 100,
                                  height: 140,
                                  fit: BoxFit.cover,
                                  placeholder: (_, __) => Container(width: 100, height: 140, color: const Color(0xFF1E1E1E)),
                                  errorWidget: (_, __, ___) => Container(width: 100, height: 140, color: const Color(0xFF1E1E1E)),
                                ),
                              ),
                              const SizedBox(height: 4),
                              SizedBox(
                                width: 100,
                                child: Text(
                                  sim.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(color: Colors.white70, fontSize: 11),
                                ),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
                ],
                const SizedBox(height: 40),
              ],
            ),
          ),

          Positioned(
            top: MediaQuery.of(context).padding.top + 6,
            left: 12,
            child: CircleAvatar(
              backgroundColor: Colors.black.withOpacity(0.5),
              child: IconButton(
                icon: const Icon(Icons.arrow_back, color: Colors.white),
                onPressed: () => Navigator.pop(context),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 90, child: Text(label, style: const TextStyle(color: Colors.white54, fontSize: 12))),
          Expanded(child: Text(value, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }
}
