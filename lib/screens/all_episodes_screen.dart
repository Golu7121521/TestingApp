import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../services/tmdb_service.dart';
import '../services/cloudstream_extractor.dart';
import '../services/torrentio_service.dart';
import 'exo_player_screen.dart';

class AllEpisodesScreen extends StatefulWidget {
  final int seriesId;
  final String seriesTitle;
  final int totalSeasons;
  final int initialSeason;

  const AllEpisodesScreen({
    super.key,
    required this.seriesId,
    required this.seriesTitle,
    required this.totalSeasons,
    this.initialSeason = 1,
  });

  @override
  State<AllEpisodesScreen> createState() => _AllEpisodesScreenState();
}

class _AllEpisodesScreenState extends State<AllEpisodesScreen> {
  late int selectedSeason;
  List<Map<String, dynamic>> episodes = [];
  bool isLoading = true;

  @override
  void initState() {
    super.initState();
    selectedSeason = widget.initialSeason;
    fetchEpisodes(selectedSeason);
  }

  Future<void> fetchEpisodes(int seasonNum) async {
    setState(() {
      selectedSeason = seasonNum;
      isLoading = true;
    });

    final data = await TmdbService.getSeasonEpisodes(widget.seriesId, seasonNum);
    if (mounted) {
      setState(() {
        episodes = data;
        isLoading = false;
      });
    }
  }

  Future<void> playEpisode(int epNum) async {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: CircularProgressIndicator(color: Color(0xFFE50914)),
      ),
    );

    String streamUrl = '';
    final queryTitle = '${widget.seriesTitle} S${selectedSeason}E$epNum';

    try {
      final directStreams = await CloudStreamExtractor.fetchDirectHindiStreams(
        movieTitle: queryTitle,
        year: '',
      );

      if (directStreams.isNotEmpty) {
        streamUrl = directStreams.first.videoUrl;
      } else {
        final imdbId = await TorrentioService.getImdbId(
          widget.seriesId,
          'tv',
          movieTitle: widget.seriesTitle,
        );

        if (imdbId != null && imdbId.isNotEmpty) {
          final torrentStreams = await TorrentioService.fetchStreams(
            imdbId: imdbId,
            isTV: true,
            season: selectedSeason,
            episode: epNum,
          );
          if (torrentStreams.isNotEmpty) {
            streamUrl = torrentStreams.first.onlineStreamUrl;
          }
        }
      }
    } catch (_) {}

    if (mounted) Navigator.pop(context);

    if (streamUrl.isEmpty) {
      streamUrl =
          'https://vidsrc.me/embed/tv?tmdb=${widget.seriesId}&season=$selectedSeason&episode=$epNum';
    }

    if (mounted) {
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => ExoPlayerScreen(
            videoUrl: streamUrl,
            title: '${widget.seriesTitle} - S${selectedSeason}E$epNum',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F0F0F),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F0F0F),
        elevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.seriesTitle,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            Text('Season $selectedSeason • ${episodes.length} Episodes',
                style: const TextStyle(fontSize: 11, color: Colors.white54)),
          ],
        ),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: DropdownButton<int>(
              value: selectedSeason,
              dropdownColor: const Color(0xFF1E1E1E),
              underline: const SizedBox(),
              icon: const Icon(Icons.arrow_drop_down, color: Color(0xFFE50914)),
              style: const TextStyle(
                  color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
              items: List.generate(
                widget.totalSeasons,
                (i) => DropdownMenuItem(
                  value: i + 1,
                  child: Text('Season ${i + 1}'),
                ),
              ),
              onChanged: (val) {
                if (val != null) fetchEpisodes(val);
              },
            ),
          )
        ],
      ),
      body: isLoading
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const CircularProgressIndicator(color: Color(0xFFE50914)),
                  const SizedBox(height: 12),
                  Text('Loading Season $selectedSeason episodes...',
                      style: const TextStyle(color: Colors.white54, fontSize: 12)),
                ],
              ),
            )
          : episodes.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Text('Failed to load episodes on this network.',
                          style: TextStyle(color: Colors.white54)),
                      const SizedBox(height: 12),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFFE50914)),
                        onPressed: () => fetchEpisodes(selectedSeason),
                        child: const Text('Retry'),
                      )
                    ],
                  ),
                )
              : ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: episodes.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 14),
                  itemBuilder: (context, idx) {
                    final ep = episodes[idx];
                    final stillPath = ep['still_path'];
                    final epNum = ep['episode_number'] ?? (idx + 1);
                    final epName = ep['name'] ?? 'Episode $epNum';
                    final overview = ep['overview'] ?? '';

                    return InkWell(
                      onTap: () => playEpisode(epNum),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(6),
                            child: Stack(
                              alignment: Alignment.center,
                              children: [
                                stillPath != null
                                    ? CachedNetworkImage(
                                        imageUrl:
                                            'https://image.tmdb.org/t/p/w300$stillPath',
                                        width: 125,
                                        height: 75,
                                        fit: BoxFit.cover,
                                        placeholder: (_, __) => Container(
                                            width: 125,
                                            height: 75,
                                            color: const Color(0xFF1E1E1E)),
                                        errorWidget: (_, __, ___) => Container(
                                            width: 125,
                                            height: 75,
                                            color: const Color(0xFF1E1E1E)),
                                      )
                                    : Container(
                                        width: 125,
                                        height: 75,
                                        color: const Color(0xFF1E1E1E)),
                                const CircleAvatar(
                                  radius: 14,
                                  backgroundColor: Colors.black54,
                                  child: Icon(Icons.play_arrow,
                                      color: Colors.white, size: 18),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '$epNum. $epName',
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      color: Colors.white,
                                      fontSize: 13),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  overview.isNotEmpty
                                      ? overview
                                      : 'Watch Episode $epNum in HD Landscape.',
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(
                                      color: Colors.white54, fontSize: 11),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
    );
  }
}
