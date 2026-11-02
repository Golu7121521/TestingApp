import 'dart:async';
import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/movie_model.dart';
import '../services/tmdb_service.dart';
import '../widgets/custom_header.dart';
import 'movie_detail_screen.dart';

class HomeScreen extends StatefulWidget {
  final VoidCallback onSearchTap;
  final Function(Movie) onAddToList;

  const HomeScreen({
    super.key,
    required this.onSearchTap,
    required this.onAddToList,
  });

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  List<Movie> bannerMovies = [];
  List<Movie> trendingIndia = [];
  List<Movie> hindiWebSeries = [];
  List<Movie> southDubbed = [];
  List<Movie> bollywoodMovies = [];
  List<Movie> globalHollywood = [];
  List<Movie> tvSerials = [];
  List<Movie> actionThrillers = [];

  bool isLoading = true;
  late PageController _pageController;
  Timer? _carouselTimer;
  int _currentBannerPage = 0;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
    loadAllFeeds();
  }

  @override
  void dispose() {
    _carouselTimer?.cancel();
    _pageController.dispose();
    super.dispose();
  }

  void startSlider() {
    _carouselTimer?.cancel();
    _carouselTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      if (bannerMovies.isNotEmpty && _pageController.hasClients) {
        _currentBannerPage = (_currentBannerPage + 1) % bannerMovies.length;
        _pageController.animateToPage(
          _currentBannerPage,
          duration: const Duration(milliseconds: 650),
          curve: Curves.easeInOut,
        );
      }
    });
  }

  Future<void> loadAllFeeds() async {
    setState(() => isLoading = true);

    try {
      // Explicit generic type <List<Movie>> compilation crash ko rokta hai
      final results = await Future.wait<List<Movie>>([
        TmdbService.getTrendingIndia().catchError((_) => <Movie>[]),
        TmdbService.getHindiWebSeries().catchError((_) => <Movie>[]),
        TmdbService.getSouthHindiDubbed().catchError((_) => <Movie>[]),
        TmdbService.getBollywoodMovies().catchError((_) => <Movie>[]),
        TmdbService.getGlobalHollywoodHindi().catchError((_) => <Movie>[]),
        TmdbService.getIndianTvSerials().catchError((_) => <Movie>[]),
        TmdbService.getActionThrillerShows().catchError((_) => <Movie>[]),
      ]);

      if (mounted) {
        setState(() {
          trendingIndia = results[0];
          hindiWebSeries = results[1];
          southDubbed = results[2];
          bollywoodMovies = results[3];
          globalHollywood = results[4];
          tvSerials = results[5];
          actionThrillers = results[6];

          bannerMovies = [
            ...trendingIndia.take(3),
            ...hindiWebSeries.take(2),
            ...southDubbed.take(2),
          ]..shuffle();

          isLoading = false;
        });

        if (bannerMovies.isNotEmpty) {
          startSlider();
        }
      }
    } catch (_) {
      if (mounted) {
        setState(() => isLoading = false);
      }
    }
  }

  void openDetail(Movie movie) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MovieDetailScreen(
          movieId: movie.id,
          mediaType: movie.mediaType,
          onAddToList: widget.onAddToList,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    if (isLoading) {
      return const Scaffold(
        backgroundColor: Color(0xFF0F0F0F),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              CircularProgressIndicator(color: Color(0xFFE50914)),
              SizedBox(height: 14),
              Text(
                "Curating Indian Entertainment Feed...",
                style: TextStyle(color: Colors.white70, fontSize: 13),
              ),
            ],
          ),
        ),
      );
    }

    final hasContent = trendingIndia.isNotEmpty ||
        hindiWebSeries.isNotEmpty ||
        southDubbed.isNotEmpty ||
        bollywoodMovies.isNotEmpty;

    return Scaffold(
      backgroundColor: const Color(0xFF0F0F0F),
      body: !hasContent
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.wifi_off_rounded,
                      color: Colors.white38, size: 56),
                  const SizedBox(height: 14),
                  const Text(
                    "Network stream issue",
                    style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.bold,
                        fontSize: 16),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    "Unable to load catalogs. Please check your data.",
                    style: TextStyle(color: Colors.white54, fontSize: 12),
                  ),
                  const SizedBox(height: 18),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFE50914),
                      padding: const EdgeInsets.symmetric(
                          horizontal: 24, vertical: 10),
                    ),
                    onPressed: loadAllFeeds,
                    child: const Text("Retry",
                        style: TextStyle(color: Colors.white)),
                  ),
                ],
              ),
            )
          : RefreshIndicator(
              color: const Color(0xFFE50914),
              backgroundColor: const Color(0xFF1E1E1E),
              onRefresh: loadAllFeeds,
              child: Stack(
                children: [
                  SingleChildScrollView(
                    physics: const AlwaysScrollableScrollPhysics(
                        parent: BouncingScrollPhysics()),
                    padding: EdgeInsets.zero,
                    child: Column(
                      children: [
                        _buildBanner(),
                        const SizedBox(height: 14),
                        if (trendingIndia.isNotEmpty)
                          _buildRail('Trending in India 🔥', trendingIndia),
                        if (hindiWebSeries.isNotEmpty)
                          _buildRail(
                              'Top Hindi Web Series & OTT Hits 📺',
                              hindiWebSeries,
                              tag: 'SERIES'),
                        if (southDubbed.isNotEmpty)
                          _buildRail(
                              'South Indian Blockbusters (Hindi Dubbed) 💥',
                              southDubbed,
                              tag: 'SOUTH'),
                        if (bollywoodMovies.isNotEmpty)
                          _buildRail('Bollywood Blockbusters 🎬',
                              bollywoodMovies),
                        if (globalHollywood.isNotEmpty)
                          _buildRail(
                              'Hollywood in Hindi (Global Hits) 🍿',
                              globalHollywood,
                              tag: 'GLOBAL'),
                        if (tvSerials.isNotEmpty)
                          _buildRail(
                              'Popular Indian TV Serials & Dramas 📻',
                              tvSerials,
                              tag: 'TV SERIAL'),
                        if (actionThrillers.isNotEmpty)
                          _buildRail('High-Octane Action & Thrillers ⚡',
                              actionThrillers),
                        const SizedBox(height: 50),
                      ],
                    ),
                  ),
                  Positioned(
                    top: 0,
                    left: 0,
                    right: 0,
                    child: CustomHeader(onSearchTap: widget.onSearchTap),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _buildBanner() {
    if (bannerMovies.isEmpty) return const SizedBox.shrink();
    return SizedBox(
      height: 490,
      child: Stack(
        alignment: Alignment.bottomCenter,
        children: [
          PageView.builder(
            controller: _pageController,
            itemCount: bannerMovies.length,
            onPageChanged: (i) => setState(() => _currentBannerPage = i),
            itemBuilder: (context, index) {
              final item = bannerMovies[index];
              final imgUrl = item.backdropPath != null && item.backdropPath!.isNotEmpty
                  ? 'https://image.tmdb.org/t/p/w780${item.backdropPath}'
                  : 'https://image.tmdb.org/t/p/w500${item.posterPath}';

              return Stack(
                fit: StackFit.expand,
                children: [
                  CachedNetworkImage(
                    imageUrl: imgUrl,
                    fit: BoxFit.cover,
                    alignment: Alignment.topCenter,
                    placeholder: (_, __) =>
                        Container(color: const Color(0xFF141414)),
                    errorWidget: (_, __, ___) =>
                        Container(color: const Color(0xFF141414)),
                  ),
                  Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        stops: const [0.35, 0.75, 1.0],
                        colors: [
                          Colors.transparent,
                          const Color(0xFF0F0F0F).withOpacity(0.85),
                          const Color(0xFF0F0F0F),
                        ],
                      ),
                    ),
                  ),
                  Positioned(
                    bottom: 24,
                    left: 16,
                    right: 16,
                    child: Column(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: item.mediaType == 'tv'
                                ? const Color(0xFFE50914)
                                : Colors.white24,
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            item.mediaType == 'tv' ? 'SERIES' : 'MOVIE',
                            style: const TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 1,
                                color: Colors.white),
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          item.title.toUpperCase(),
                          textAlign: TextAlign.center,
                          maxLines: 2,
                          style: const TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.w900,
                            letterSpacing: 1.2,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          item.overview.isNotEmpty
                              ? item.overview
                              : "Top Indian Entertainment",
                          maxLines: 2,
                          textAlign: TextAlign.center,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              color: Colors.white70, fontSize: 12),
                        ),
                        const SizedBox(height: 14),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            ElevatedButton.icon(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: const Color(0xFFE50914),
                                foregroundColor: Colors.white,
                                shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(6)),
                              ),
                              icon: const Icon(Icons.play_arrow),
                              label: const Text('Watch Now',
                                  style:
                                      TextStyle(fontWeight: FontWeight.bold)),
                              onPressed: () => openDetail(item),
                            ),
                            const SizedBox(width: 14),
                            OutlinedButton.icon(
                              style: OutlinedButton.styleFrom(
                                side: const BorderSide(color: Colors.white38),
                                foregroundColor: Colors.white,
                                shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(6)),
                              ),
                              icon: const Icon(Icons.add),
                              label: const Text('My List',
                                  style:
                                      TextStyle(fontWeight: FontWeight.bold)),
                              onPressed: () => widget.onAddToList(item),
                            ),
                          ],
                        )
                      ],
                    ),
                  )
                ],
              );
            },
          ),
          Positioned(
            bottom: 8,
            child: Row(
              children: List.generate(
                bannerMovies.length,
                (i) => Container(
                  width: _currentBannerPage == i ? 16 : 6,
                  height: 4,
                  margin: const EdgeInsets.symmetric(horizontal: 2),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(2),
                    color: _currentBannerPage == i
                        ? const Color(0xFFE50914)
                        : Colors.white30,
                  ),
                ),
              ),
            ),
          )
        ],
      ),
    );
  }

  Widget _buildRail(String title, List<Movie> list, {String? tag}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Text(title,
                        style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                            color: Colors.white)),
                    if (tag != null) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: const Color(0xFFE50914),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(tag,
                            style: const TextStyle(
                                fontSize: 9,
                                fontWeight: FontWeight.bold,
                                color: Colors.white)),
                      )
                    ]
                  ],
                ),
                const Text('See All >',
                    style: TextStyle(color: Colors.white54, fontSize: 12)),
              ],
            ),
          ),
          const SizedBox(height: 10),
          SizedBox(
            height: 175,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              scrollDirection: Axis.horizontal,
              itemCount: list.length,
              separatorBuilder: (_, __) => const SizedBox(width: 10),
              itemBuilder: (context, i) {
                final item = list[i];
                final posterUrl = item.posterPath != null && item.posterPath!.isNotEmpty
                    ? 'https://image.tmdb.org/t/p/w342${item.posterPath}'
                    : '';

                return InkWell(
                  onTap: () => openDetail(item),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(6),
                        child: CachedNetworkImage(
                          imageUrl: posterUrl,
                          width: 105,
                          height: 145,
                          fit: BoxFit.cover,
                          placeholder: (_, __) => Container(
                            width: 105,
                            height: 145,
                            color: const Color(0xFF1E1E1E),
                            child: const Center(
                              child: CircularProgressIndicator(
                                  color: Color(0xFFE50914), strokeWidth: 2),
                            ),
                          ),
                          errorWidget: (_, __, ___) => Container(
                            width: 105,
                            height: 145,
                            color: const Color(0xFF222222),
                            child: const Icon(Icons.broken_image,
                                color: Colors.white30),
                          ),
                        ),
                      ),
                      const SizedBox(height: 5),
                      SizedBox(
                        width: 105,
                        child: Text(
                          item.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                              color: Colors.white70, fontSize: 11),
                        ),
                      )
                    ],
                  ),
                );
              },
            ),
          )
        ],
      ),
    );
  }
}
