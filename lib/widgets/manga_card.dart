import 'package:flutter/material.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../models/reading_progress.dart';
import 'cover_image.dart';

class MangaCard extends StatelessWidget {
  final MangaItem manga;
  final ReadingProgress? progress;
  final VoidCallback onTap;

  const MangaCard({
    super.key,
    required this.manga,
    required this.progress,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: AspectRatio(
          aspectRatio: 0.65,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Top 70% — cover image
              Expanded(
                flex: 70,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    CoverImage(manga: manga),
                    if (progress != null)
                      Positioned(
                        top: 6,
                        right: 6,
                        child: _ReadingBadge(),
                      ),
                  ],
                ),
              ),
              // Bottom 30% — info
              Expanded(
                flex: 30,
                child: Container(
                  color: AppTheme.surface,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        manga.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppTheme.onBackground,
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${manga.chapterCount} ch.',
                        style: const TextStyle(
                          color: AppTheme.textSecondary,
                          fontSize: 10,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ReadingBadge extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
      decoration: BoxDecoration(
        color: AppTheme.primary,
        borderRadius: BorderRadius.circular(8),
      ),
      child: const Text(
        'READING',
        style: TextStyle(
          color: Colors.black,
          fontSize: 8,
          fontWeight: FontWeight.bold,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}
