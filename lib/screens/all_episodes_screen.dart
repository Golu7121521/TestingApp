import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../services/tmdb_service.dart';
import '../services/download_service.dart';

class AllEpisodesScreen extends StatefulWidget {
  final int seriesId;
  final String seriesTitle;
  final int totalSeasons;
  final int initialSeason;
  final VoidCallback? onSwitchToDownloads;

  const AllEpisodesScreen({
    super.key,
    required this.seriesId,
    required this.seriesTitle,
    required this.totalSeasons,
    this.initialSeason = 1,
    this.onSwitchToDownloads,
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

  Future<void> openEpisodeDownloadSelector(int epNum, String? stillPath) async {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => const Center(
        child: CircularProgressIndicator(color: Color(0xFFE50914)),
      ),
    );

    List<TorrentStreamItem> torrentStreams = [];

    try {
      final imdbId = await TorrentioService.getImdbId(
        widget.seriesId,
        'tv',
        movieTitle: widget.seriesTitle,
      );

      if (imdbId != null && imdbId.isNotEmpty) {
        torrentStreams = await TorrentioService.getTorrentStreams(
          imdbId: imdbId,
          isTV: true,
          season: selectedSeason,
          episode: epNum,
        );
      }
    } catch (_) {}

    if (mounted) Navigator.pop(context);

    if (torrentStreams.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('No active torrent files found for S${selectedSeason}E$epNum.'),
            backgroundColor: const Color(0xFFE50914),
          ),
        );
      }
      return;
    }

    void showConfirmationDialog(TorrentStreamItem item) {
      final episodeTitle = '${widget.seriesTitle} - S${selectedSeason}E$epNum';

      showDialog(
        context: context,
        builder: (ctx) => AlertDialog(
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
              Text(episodeTitle, style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              _dialogRow('Quality', item.quality),
              _dialogRow('Audio Language', item.audioLanguage),
              _dialogRow('File Size', item.size),
              _dialogRow('Seeds Active', '${item.seeders} Seeds'),
              const SizedBox(height: 12),
              const Text(
                'Will be downloaded to /Download/CineFlix for high performance offline playback.',
                style: TextStyle(color: Colors.white38, fontSize: 11),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel', style: TextStyle(color: Colors.white54)),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFE50914)),
              onPressed: () async {
                Navigator.pop(ctx);

                final magnet = TorrentioService.buildMagnetUri(item.infoHash, item.title);

                // Exact parameter mapping matching new DownloadManager
                await DownloadManager.instance.startDownload(
                  id: '${widget.seriesId}_s${selectedSeason}_e${epNum}_${item.infoHash}',
                  title: episodeTitle,
                  magnetUri: magnet,
                  quality: item.quality,
                  audioLanguage: item.audioLanguage,
                  sizeLabel: item.size,
                  posterUrl: stillPath ?? '',
                );

                if (mounted) {
                  Navigator.of(context).popUntil((route) => route.isFirst);
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

    if (mounted) {
      showModalBottomSheet(
        context: context,
        backgroundColor: const Color(0xFF141414),
        isScrollControlled: true,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
        ),
        builder: (ctx) {
          return SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.arrow_circle_down_rounded, color: Color(0xFFE50914), size: 24),
                          const SizedBox(width: 8),
                          Text(
                            'S${selectedSeason}E$epNum Download Links',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 15,
                              fontWeight: FontWeight.bold,
                            ),
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
                    constraints: BoxConstraints(
                      maxHeight: MediaQuery.of(context).size.height * 0.55,
                    ),
                    child: ListView.separated(
                      shrinkWrap: true,
                      itemCount: torrentStreams.length,
                      separatorBuilder: (_, __) => const Divider(color: Colors.white12, height: 1),
                      itemBuilder: (context, i) {
                        final s = torrentStreams[i];
                        return ListTile(
                          contentPadding: const EdgeInsets.symmetric(vertical: 4),
                          leading: Icon(
                            Icons.download_rounded,
                            color: s.isHindi ? const Color(0xFFE50914) : Colors.white70,
                            size: 30,
                          ),
                          title: Text(
                            s.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          subtitle: Text(
                            '${s.quality}  •  ${s.size}  •  ${s.audioLanguage}  •  👤 ${s.seeders} Seeds',
                            style: TextStyle(
                              color: s.isHindi ? const Color(0xFFE50914) : Colors.white54,
                              fontSize: 11,
                            ),
                          ),
                          trailing: const Icon(Icons.arrow_forward_ios, size: 14, color: Colors.white38),
                          onTap: () {
                            Navigator.pop(ctx);
                            showConfirmationDialog(s);
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

  Widget _dialogRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.white54, fontSize: 12)),
          Text(value, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold)),
        ],
      ),
    );
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
            Text(widget.seriesTitle, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            Text('Season $selectedSeason • ${episodes.length} Episodes', style: const TextStyle(fontSize: 11, color: Colors.white54)),
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
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
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
                  Text('Loading Season $selectedSeason episodes...', style: const TextStyle(color: Colors.white54, fontSize: 12)),
                ],
              ),
            )
          : episodes.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Text('Failed to load episodes on this network.', style: TextStyle(color: Colors.white54)),
                      const SizedBox(height: 12),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFE50914)),
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
                      onTap: () => openEpisodeDownloadSelector(epNum, stillPath),
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
                                        imageUrl: 'https://image.tmdb.org/t/p/w300$stillPath',
                                        width: 125,
                                        height: 75,
                                        fit: BoxFit.cover,
                                        placeholder: (_, __) => Container(width: 125, height: 75, color: const Color(0xFF1E1E1E)),
                                        errorWidget: (_, __, ___) => Container(width: 125, height: 75, color: const Color(0xFF1E1E1E)),
                                      )
                                    : Container(width: 125, height: 75, color: const Color(0xFF1E1E1E)),
                                const CircleAvatar(
                                  radius: 14,
                                  backgroundColor: Colors.black54,
                                  child: Icon(Icons.download_rounded, color: Colors.white, size: 18),
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
                                  style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 13),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  overview.isNotEmpty ? overview : 'Tap to select quality and download.',
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: const TextStyle(color: Colors.white54, fontSize: 11),
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
