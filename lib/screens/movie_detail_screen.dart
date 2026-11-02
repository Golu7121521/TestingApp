import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/movie_model.dart';
import '../services/tmdb_service.dart';
import '../services/download_service.dart';
import 'trailer_player_screen.dart';
import 'all_episodes_screen.dart';

class MovieDetailScreen extends StatefulWidget {
  final int movieId;
  final String mediaType;
  final Function(Movie) onAddToList;
  final VoidCallback? onSwitchToDownloads;

  const MovieDetailScreen({
    super.key,
    required this.movieId,
    required this.mediaType,
    required this.onAddToList,
    this.onSwitchToDownloads,
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

  // DOWNLOAD FLOW
  Future<void> openDownloadSelector({int season = 1, int episode = 1}) async {
    final resolvedType = data!['resolved_media_type'] ?? widget.mediaType;
    final title = (data!['title'] ?? data!['name'] ?? 'Movie').toString();
    final posterPath = data!['poster_path'] ?? '';
    final isTV = resolvedType == 'tv';

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: CircularProgressIndicator(color: Color(0xFFE50914)),
      ),
    );

    List<TorrentStreamItem> streams = [];
    try {
      final imdbId = await TorrentioService.getImdbId(widget.movieId, resolvedType, movieTitle: title);
      if (imdbId != null && imdbId.isNotEmpty) {
        streams = await TorrentioService.getTorrentStreams(
          imdbId: imdbId,
          isTV: isTV,
          season: season,
          episode: episode,
        );
      }
    } catch (_) {}

    if (mounted) Navigator.pop(context); // Close loader

    if (streams.isEmpty) {
      if (mounted) {
        showDialog(
          context: context,
          builder: (c) => AlertDialog(
            backgroundColor: const Color(0xFF1E1E1E),
            title: const Text('No Download Sources', style: TextStyle(color: Colors.white, fontSize: 16)),
            content: Text('No active torrent files found for "$title".', style: const TextStyle(color: Colors.white70, fontSize: 13)),
            actions: [
              TextButton(onPressed: () => Navigator.pop(c), child: const Text('OK', style: TextStyle(color: Color(0xFFE50914)))),
            ],
          ),
        );
      }
      return;
    }

    // Step 2: Show Stream Selector Sheet (Hindi on Top)
    if (mounted) {
      showModalBottomSheet(
        context: context,
        backgroundColor: const Color(0xFF161616),
        isScrollControlled: true,
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
        builder: (ctx) {
          return SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                mainAxisSize: dynamic,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.download_for_offline, color: Color(0xFFE50914), size: 22),
                          const SizedBox(width: 8),
                          Text(
                            isTV ? 'Select Episode to Download' : 'Select Download Quality',
                            style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, color: Colors.white54, size: 20),
                        onPressed: () => Navigator.pop(ctx),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  ConstrainedBox(
                    constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.55),
                    child: ListView.separated(
                      shrinkWrap: true,
                      itemCount: streams.length,
                      separatorBuilder: (_, __) => const Divider(color: Colors.white12, height: 1),
                      itemBuilder: (context, idx) {
                        final s = streams[idx];
                        return ListTile(
                          contentPadding: const EdgeInsets.symmetric(vertical: 4),
                          leading: Icon(
                            Icons.arrow_circle_down_rounded,
                            color: s.isHindi ? const Color(0xFFE50914) : Colors.white70,
                            size: 32,
                          ),
                          title: Text(
                            s.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.bold),
                          ),
                          subtitle: Text(
                            '${s.quality}  •  ${s.size}  •  ${s.audioLanguage}  •  👤 ${s.seeders} seeds',
                            style: TextStyle(
                              color: s.isHindi ? const Color(0xFFE50914) : Colors.white54,
                              fontSize: 11,
                            ),
                          ),
                          trailing: const Icon(Icons.file_download, color: Colors.white70, size: 20),
                          onTap: () {
                            Navigator.pop(ctx);
                            _showConfirmationDialog(s, title, posterPath, isTV, season, episode);
                          },
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      );
    }
  }

  // Step 3: Confirmation Dialog
  void _showConfirmationDialog(
    TorrentStreamItem item,
    String title,
    String posterPath,
    bool isTV,
    int season,
    int episode,
  ) {
    final finalTitle = isTV ? '$title (S${season}E$episode)' : title;

    showDialog(
      context: context,
      builder: (c) => AlertDialog(
        backgroundColor: const Color(0xFF1E1E1E),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        title: const Row(
          children: [
            Icon(Icons.downloading_rounded, color: Color(0xFFE50914)),
            SizedBox(width: 8),
            Text('Confirm Download', style: TextStyle(color: Colors.white, fontSize: 16)),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(finalTitle, style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            _infoRow('Quality', item.quality),
            _infoRow('Audio Language', item.audioLanguage),
            _infoRow('File Size', item.size),
            _infoRow('Peers / Seeds', '${item.seeders} Seeds active'),
            const SizedBox(height: 12),
            const Text(
              'Once started, file will be saved directly to your phone storage for offline playback.',
              style: TextStyle(color: Colors.white38, fontSize: 11),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(c),
            child: const Text('Cancel', style: TextStyle(color: Colors.white54)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFE50914)),
            onPressed: () async {
              Navigator.pop(c); // Confirm Dialog dismiss

              final magnet = TorrentioService.buildMagnetUri(item.infoHash, item.title);

              // Exact parameter mapping matching new DownloadManager
              await DownloadManager.instance.startDownload(
                id: '${widget.movieId}_${item.infoHash}',
                title: finalTitle,
                magnetUri: magnet,
                quality: item.quality,
                audioLanguage: item.audioLanguage,
                sizeLabel: item.size,
                posterUrl: posterPath,
              );

              // Guaranteed navigation: Close detail screen and jump directly to Downloads Tab
              if (mounted) {
                if (Navigator.of(context).canPop()) {
                  Navigator.of(context).pop();
                }
                RootNav.index.value = RootNav.downloadsTab;
                widget.onSwitchToDownloads?.call();
              }
            },
            child: const Text('Start Download', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  Widget _infoRow(String label, String val) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.white54, fontSize: 12)),
          Text(val, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }

  void playTrailer(String key, List<dynamic> videos) {
    if (key.isEmpty) return;
    final title = (data!['title'] ?? data!['name'] ?? 'Trailer').toString();
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TrailerPlayerScreen(initialVideoKey: key, title: title, allVideos: videos),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Scaffold(
        backgroundColor: Color(0xFF0F0F0F),
        body: Center(child: CircularProgressIndicator(color: Color(0xFFE50914))),
      );
    }

    if (data == null || data!.isEmpty) {
      return Scaffold(
        backgroundColor: const Color(0xFF0F0F0F),
        appBar: AppBar(backgroundColor: Colors.transparent),
        body: Center(
          child: ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFE50914)),
            onPressed: loadMovieDetails,
            child: const Text('Try Again', style: TextStyle(color: Colors.white)),
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

    final videos = ((data!['videos']?['results'] as List?) ?? []).where((v) => v['site'] == 'YouTube').toList();
    String mainTrailerKey = '';
    if (videos.isNotEmpty) mainTrailerKey = videos.first['key'] ?? '';

    return Scaffold(
      backgroundColor: const Color(0xFF0F0F0F),
      body: Stack(
        children: [
          SingleChildScrollView(
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
                          colors: [Colors.transparent, const Color(0xFF0F0F0F).withOpacity(0.7), const Color(0xFF0F0F0F)],
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
                            style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w900, color: Colors.white),
                          ),
                          const SizedBox(height: 6),
                          Text('$year • Movies • U/A 13+', style: const TextStyle(color: Colors.white60, fontSize: 12)),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),

                // DOWNLOAD TO WATCH BUTTON
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFFE50914),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 13),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                          ),
                          icon: const Icon(Icons.download_rounded, size: 22),
                          label: const Text('Download to Watch', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                          onPressed: () => openDownloadSelector(),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: OutlinedButton.icon(
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(color: Colors.white30),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 13),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                          ),
                          icon: const Icon(Icons.add, size: 20),
                          label: const Text('Add to My List', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                          onPressed: () => widget.onAddToList(Movie.fromJson(data!, defaultType: resolvedType)),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // EPISODES (FOR TV SHOWS)
                if (isTV) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Episodes', style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold, color: Colors.white)),
                        const SizedBox(height: 12),
                        GridView.builder(
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          itemCount: episodesList.take(6).length,
                          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 2,
                            childAspectRatio: 1.45,
                            crossAxisSpacing: 10,
                            mainAxisSpacing: 10,
                          ),
                          itemBuilder: (context, idx) {
                            final ep = episodesList[idx];
                            final epNum = ep['episode_number'] ?? (idx + 1);
                            return InkWell(
                              onTap: () => openDownloadSelector(season: selectedSeason, episode: epNum),
                              child: Container(
                                color: const Color(0xFF1E1E1E),
                                child: Center(
                                  child: Text('Download EP $epNum', style: const TextStyle(color: Colors.white, fontSize: 12)),
                                ),
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                ],

                // OVERVIEW
                Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Text(
                    data!['overview'] ?? 'No synopsis available.',
                    style: const TextStyle(color: Colors.white70, fontSize: 13, height: 1.4),
                  ),
                ),
              ],
            ),
          ),
          Positioned(
            top: MediaQuery.of(context).padding.top + 6,
            left: 12,
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
    );
  }
}
