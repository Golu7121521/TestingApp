import 'package:flutter/material.dart';

class CustomHeader extends StatelessWidget {
  final VoidCallback onSearchTap;

  const CustomHeader({super.key, required this.onSearchTap});

  @override
  Widget build(BuildContext context) {
    final statusBarHeight = MediaQuery.of(context).padding.top;

    return RepaintBoundary(
      child: Container(
        padding: EdgeInsets.only(
          top: statusBarHeight + 6,
          left: 16,
          right: 8,
          bottom: 20,
        ),
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            stops: [0.0, 0.4, 0.7, 1.0],
            colors: [
              Color(0xE6000000), // Solid Dark on top
              Color(0x99000000),
              Color(0x33000000),
              Colors.transparent, // Fade into banner
            ],
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    color: const Color(0xFFE50914),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: const Icon(
                    Icons.play_arrow_rounded,
                    color: Colors.white,
                    size: 20,
                  ),
                ),
                const SizedBox(width: 8),
                RichText(
                  text: const TextSpan(
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 0.5,
                    ),
                    children: [
                      TextSpan(
                        text: 'Cine',
                        style: TextStyle(color: Colors.white),
                      ),
                      TextSpan(
                        text: 'Flix',
                        style: TextStyle(color: Color(0xFFE50914)),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            IconButton(
              icon: const Icon(Icons.search, color: Colors.white, size: 26),
              splashRadius: 24,
              tooltip: 'Search Movies & Shows',
              onPressed: onSearchTap,
            ),
          ],
        ),
      ),
    );
  }
}
