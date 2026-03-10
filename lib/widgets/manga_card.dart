import 'dart:io';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../models/reading_progress.dart';
import '../services/thumbnail_service.dart';

class MangaCard extends StatefulWidget {
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
  State<MangaCard> createState() => _MangaCardState();
}

class _MangaCardState extends State<MangaCard> {
  Future<File?>? _thumbFuture;

  @override
  void initState() {
    super.initState();
    _thumbFuture = ThumbnailService.instance.getThumbnailFile(widget.manga);
  }

  @override
  void didUpdateWidget(MangaCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.manga.id != widget.manga.id) {
      _thumbFuture = ThumbnailService.instance.getThumbnailFile(widget.manga);
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: widget.onTap,
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
                    _buildCover(),
                    if (widget.progress != null)
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
                  padding: const EdgeInsets.symmetric(
                      horizontal: 6, vertical: 4),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        widget.manga.title,
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
                        '${widget.manga.chapterCount} ch.',
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

  Widget _buildCover() {
    return FutureBuilder<File?>(
      future: _thumbFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.done &&
            snapshot.data != null) {
          return Image.file(
            snapshot.data!,
            fit: BoxFit.cover,
          );
        }
        return _Skeleton();
      },
    );
  }
}

class _Skeleton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppTheme.surface,
      child: Center(
        child: Icon(
          Icons.menu_book,
          size: 36,
          color: AppTheme.onBackground.withValues(alpha: 0.3),
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
