import 'package:flutter/material.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../models/movie_model.dart';
import 'movie_detail_screen.dart';

class MyListScreen extends StatefulWidget {
  final List<Movie> myList;
  final Function(int) onRemove;

  const MyListScreen({
    super.key,
    required this.myList,
    required this.onRemove,
  });

  @override
  State<MyListScreen> createState() => _MyListScreenState();
}

class _MyListScreenState extends State<MyListScreen> {
  void _openDetail(Movie movie) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MovieDetailScreen(
          movieId: movie.id,
          mediaType: movie.mediaType,
          onAddToList: (_) {},
        ),
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
        title: Text(
          'My List (${widget.myList.length})',
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
      ),
      body: widget.myList.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(22),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: const Color(0xFF1A1A1A),
                      border: Border.all(color: Colors.white10),
                    ),
                    child: const Icon(
                      Icons.video_library_outlined,
                      size: 56,
                      color: Colors.white30,
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    "Your Watchlist is Empty",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    "Tap '+ My List' on any movie or show to save it here.",
                    style: TextStyle(color: Colors.white54, fontSize: 12),
                  ),
                ],
              ),
            )
          : ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              itemCount: widget.myList.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, i) {
                final m = widget.myList[i];
                final isTV = m.mediaType == 'tv';
                final mediaLabel = isTV ? 'Series' : 'Movie';
                final yearText = m.releaseDate.isNotEmpty ? ' • ${m.releaseDate}' : '';

                return InkWell(
                  onTap: () => _openDetail(m),
                  borderRadius: BorderRadius.circular(8),
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFF161616),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(6),
                          child: m.realPosterUrl.isNotEmpty
                              ? CachedNetworkImage(
                                  imageUrl: m.realPosterUrl,
                                  width: 60,
                                  height: 85,
                                  fit: BoxFit.cover,
                                  placeholder: (_, __) => Container(
                                    width: 60,
                                    height: 85,
                                    color: const Color(0xFF1E1E1E),
                                  ),
                                  errorWidget: (_, __, ___) => Container(
                                    width: 60,
                                    height: 85,
                                    color: const Color(0xFF1E1E1E),
                                    child: const Icon(Icons.movie, color: Colors.white30),
                                  ),
                                )
                              : Container(
                                  width: 60,
                                  height: 85,
                                  color: const Color(0xFF1E1E1E),
                                  child: const Icon(Icons.movie, color: Colors.white30),
                                ),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                m.title,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 14,
                                  color: Colors.white,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Row(
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                    decoration: BoxDecoration(
                                      color: isTV ? const Color(0xFFE50914) : Colors.white24,
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                    child: Text(
                                      mediaLabel.toUpperCase(),
                                      style: const TextStyle(
                                        fontSize: 9,
                                        fontWeight: FontWeight.bold,
                                        color: Colors.white,
                                      ),
                                    ),
                                  ),
                                  if (yearText.isNotEmpty)
                                    Text(
                                      yearText,
                                      style: const TextStyle(color: Colors.white54, fontSize: 12),
                                    ),
                                ],
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          icon: const Icon(
                            Icons.delete_outline_rounded,
                            color: Color(0xFFE50914),
                            size: 24,
                          ),
                          onPressed: () => widget.onRemove(i),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
}
