import 'dart:async';
import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/movie_model.dart';
import '../services/tmdb_service.dart';
import 'movie_detail_screen.dart';

class SearchScreen extends StatefulWidget {
  final Function(Movie)? onAddToList;
  const SearchScreen({super.key, this.onAddToList});

  @override
  State<SearchScreen> createState() => _SearchScreenState();
}

class _SearchScreenState extends State<SearchScreen> with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  final TextEditingController _ctrl = TextEditingController();
  Timer? _debounce;
  int _searchRequestId = 0;

  List<Movie> searchResults = [];
  List<Movie> dynamicTopSearches = [];
  bool isSearching = false;
  bool isFetching = false;
  bool isLoadingTopSearches = true;

  int _selectedFilterIndex = 0;
  final List<String> _filters = [
    'All',
    'Web Series',
    'Bollywood',
    'South Dubbed',
    'Hollywood in Hindi',
  ];

  @override
  void initState() {
    super.initState();
    loadLiveTopSearches();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> loadLiveTopSearches() async {
    try {
      final liveTrends = await TmdbService.getTrendingTopSearches();
      if (mounted) {
        setState(() {
          dynamicTopSearches = liveTrends;
          isLoadingTopSearches = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() => isLoadingTopSearches = false);
      }
    }
  }

  void onSearchChanged(String query) {
    if (_debounce?.isActive ?? false) _debounce!.cancel();

    _debounce = Timer(const Duration(milliseconds: 350), () async {
      final q = query.trim();
      final currentId = ++_searchRequestId;

      if (q.isEmpty) {
        if (mounted) {
          setState(() {
            searchResults = [];
            isSearching = _selectedFilterIndex != 0;
            isFetching = false;
          });
        }
        return;
      }

      setState(() {
        isSearching = true;
        isFetching = true;
      });

      try {
        final data = await TmdbService.searchMovies(q);
        if (mounted && currentId == _searchRequestId) {
          setState(() {
            searchResults = data;
            isFetching = false;
          });
        }
      } catch (_) {
        if (mounted && currentId == _searchRequestId) {
          setState(() => isFetching = false);
        }
      }
    });
  }

  Future<void> applyFilter(int index) async {
    setState(() {
      _selectedFilterIndex = index;
      isSearching = index != 0;
      isFetching = index != 0;
    });

    if (index == 0) {
      _ctrl.clear();
      setState(() {
        searchResults = [];
        isFetching = false;
      });
      return;
    }

    List<Movie> filteredData = [];
    try {
      switch (index) {
        case 1:
          filteredData = await TmdbService.getHindiWebSeries();
          break;
        case 2:
          filteredData = await TmdbService.getBollywoodMovies();
          break;
        case 3:
          filteredData = await TmdbService.getSouthHindiDubbed();
          break;
        case 4:
          filteredData = await TmdbService.getGlobalHollywoodHindi();
          break;
      }
    } catch (_) {}

    if (mounted) {
      setState(() {
        searchResults = filteredData;
        isFetching = false;
      });
    }
  }

  void openDetail(Movie movie) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MovieDetailScreen(
          movieId: movie.id,
          mediaType: movie.mediaType,
          onAddToList: widget.onAddToList ?? (_) {},
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);

    final displayList = isSearching ? searchResults : dynamicTopSearches;

    return Scaffold(
      backgroundColor: const Color(0xFF0F0F0F),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F0F0F),
        elevation: 0,
        title: const Text(
          'Search Movies & Series',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
      ),
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              decoration: BoxDecoration(
                color: const Color(0xFF1F1F1F),
                borderRadius: BorderRadius.circular(8),
              ),
              child: TextField(
                controller: _ctrl,
                style: const TextStyle(color: Colors.white, fontSize: 14),
                decoration: InputDecoration(
                  prefixIcon: const Icon(Icons.search, color: Colors.white54),
                  suffixIcon: _ctrl.text.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.clear, color: Colors.white54, size: 18),
                          onPressed: () {
                            _ctrl.clear();
                            onSearchChanged('');
                          },
                        )
                      : null,
                  hintText: 'Search Hindi movies, web series, south, serials...',
                  hintStyle: const TextStyle(color: Colors.white38, fontSize: 13),
                  border: InputBorder.none,
                  contentPadding: const EdgeInsets.symmetric(vertical: 12),
                ),
                onChanged: onSearchChanged,
              ),
            ),
            const SizedBox(height: 12),

            SizedBox(
              height: 36,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: _filters.length,
                separatorBuilder: (_, __) => const SizedBox(width: 8),
                itemBuilder: (context, index) {
                  final isSelected = _selectedFilterIndex == index;
                  return ChoiceChip(
                    label: Text(
                      _filters[index],
                      style: TextStyle(
                        color: isSelected ? Colors.white : Colors.white70,
                        fontSize: 12,
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                      ),
                    ),
                    selected: isSelected,
                    selectedColor: const Color(0xFFE50914),
                    backgroundColor: const Color(0xFF1E1E1E),
                    showCheckmark: false,
                    side: BorderSide.none,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(6)),
                    onSelected: (_) => applyFilter(index),
                  );
                },
              ),
            ),
            const SizedBox(height: 16),

            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  isSearching
                      ? (_selectedFilterIndex == 0 ? 'Search Results' : _filters[_selectedFilterIndex])
                      : 'Trending Searches in India 🔥',
                  style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
                ),
                if (isFetching || (isLoadingTopSearches && !isSearching))
                  const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(color: Color(0xFFE50914), strokeWidth: 2),
                  ),
              ],
            ),
            const SizedBox(height: 12),

            Expanded(
              child: isSearching && !isFetching && searchResults.isEmpty
                  ? const Center(
                      child: Text(
                        'No results found for your search.',
                        style: TextStyle(color: Colors.white54, fontSize: 13),
                      ),
                    )
                  : !isSearching && isLoadingTopSearches
                      ? const Center(
                          child: CircularProgressIndicator(color: Color(0xFFE50914)),
                        )
                      : ListView.separated(
                          itemCount: displayList.length,
                          separatorBuilder: (_, __) => const SizedBox(height: 10),
                          itemBuilder: (context, i) {
                            final item = displayList[i];
                            final isTV = item.mediaType == 'tv';
                            final posterUrl = item.posterPath != null && item.posterPath!.isNotEmpty
                                ? 'https://image.tmdb.org/t/p/w185${item.posterPath}'
                                : '';

                            return InkWell(
                              onTap: () => openDetail(item),
                              borderRadius: BorderRadius.circular(6),
                              child: Row(
                                children: [
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(6),
                                    child: CachedNetworkImage(
                                      imageUrl: posterUrl,
                                      width: 58,
                                      height: 82,
                                      fit: BoxFit.cover,
                                      placeholder: (_, __) => Container(
                                        width: 58,
                                        height: 82,
                                        color: const Color(0xFF1E1E1E),
                                      ),
                                      errorWidget: (_, __, ___) => Container(
                                        width: 58,
                                        height: 82,
                                        color: const Color(0xFF1E1E1E),
                                        child: const Icon(Icons.movie, color: Colors.white24),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 14),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          item.title,
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: const TextStyle(
                                            fontWeight: FontWeight.bold,
                                            color: Colors.white,
                                            fontSize: 14,
                                          ),
                                        ),
                                        const SizedBox(height: 5),
                                        Row(
                                          children: [
                                            Container(
                                              padding: const EdgeInsets.symmetric(
                                                  horizontal: 6, vertical: 2),
                                              decoration: BoxDecoration(
                                                color: isTV
                                                    ? const Color(0xFFE50914)
                                                    : const Color(0xFF2A2A2A),
                                                borderRadius: BorderRadius.circular(4),
                                              ),
                                              child: Text(
                                                isTV ? 'SERIES' : 'MOVIE',
                                                style: TextStyle(
                                                  fontSize: 9,
                                                  fontWeight: FontWeight.bold,
                                                  color: isTV ? Colors.white : Colors.white70,
                                                  letterSpacing: 0.5,
                                                ),
                                              ),
                                            ),
                                            if (item.releaseDate.isNotEmpty) ...[
                                              const SizedBox(width: 8),
                                              Text(
                                                item.releaseDate,
                                                style: const TextStyle(
                                                    color: Colors.white54, fontSize: 12),
                                              ),
                                            ],
                                          ],
                                        ),
                                        const SizedBox(height: 4),
                                        if (item.overview.isNotEmpty)
                                          Text(
                                            item.overview,
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                            style: const TextStyle(
                                                color: Colors.white38, fontSize: 11),
                                          ),
                                      ],
                                    ),
                                  ),
                                  const Icon(Icons.arrow_forward_ios,
                                      size: 13, color: Colors.white38),
                                ],
                              ),
                            );
                          },
                        ),
            ),
          ],
        ),
      ),
    );
  }
}
