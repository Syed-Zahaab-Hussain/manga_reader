import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../models/reading_progress.dart';
import '../services/progress_service.dart';
import '../services/thumbnail_service.dart';

class DetailScreen extends StatefulWidget {
  final Object? extra;
  const DetailScreen({super.key, this.extra});

  @override
  State<DetailScreen> createState() => _DetailScreenState();
}

class _DetailScreenState extends State<DetailScreen> {
  late final MangaItem _manga;
  ReadingProgress? _progress;
  bool _ascending = true;

  @override
  void initState() {
    super.initState();
    _manga = widget.extra as MangaItem;
    _loadProgress();
  }

  Future<void> _loadProgress() async {
    final p = await ProgressService.get(_manga.id);
    if (mounted) setState(() => _progress = p);
  }

  List<int> get _sortedIndices {
    final indices = List.generate(_manga.chapters.length, (i) => i);
    if (!_ascending) {
      return indices.reversed.toList();
    }
    return indices;
  }

  void _openReader(int chapterIndex) {
    context.push('/reader', extra: {
      'manga': _manga,
      'chapterIndex': chapterIndex,
    });
  }

  void _continueReading() {
    if (_progress == null) return;
    _openReader(_progress!.chapterIndex);
  }

  @override
  Widget build(BuildContext context) {
    final sorted = _sortedIndices;

    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.background,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        title: Text(
          _manga.title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ),
      body: CustomScrollView(
        slivers: [
          // Cover image with gradient + title
          SliverToBoxAdapter(child: _buildCoverSection()),

          // Continue reading button
          if (_progress != null)
            SliverToBoxAdapter(child: _buildContinueReading()),

          // Chapter count + sort toggle
          SliverToBoxAdapter(child: _buildChapterHeader()),

          // Chapter list
          SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) {
                final chapterIndex = sorted[index];
                return _buildChapterTile(chapterIndex);
              },
              childCount: sorted.length,
            ),
          ),

          // Bottom padding
          const SliverPadding(padding: EdgeInsets.only(bottom: 24)),
        ],
      ),
    );
  }

  Widget _buildCoverSection() {
    return SizedBox(
      height: 280,
      child: Stack(
        fit: StackFit.expand,
        children: [
          // Cover image
          _CoverImage(manga: _manga),

          // Gradient overlay at bottom
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            height: 120,
            child: Container(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.transparent,
                    AppTheme.background,
                  ],
                ),
              ),
            ),
          ),

          // Title over gradient
          Positioned(
            left: 16,
            right: 16,
            bottom: 12,
            child: Text(
              _manga.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AppTheme.onBackground,
                fontSize: 22,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildContinueReading() {
    final p = _progress!;
    final chapter = _manga.chapters[p.chapterIndex];
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: SizedBox(
        width: double.infinity,
        height: 48,
        child: ElevatedButton.icon(
          onPressed: _continueReading,
          icon: const Icon(Icons.play_arrow_rounded, size: 22),
          label: Text(
            'Continue — ${chapter.title}, Page ${p.pageIndex + 1}',
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontWeight: FontWeight.bold),
          ),
          style: ElevatedButton.styleFrom(
            backgroundColor: AppTheme.primary,
            foregroundColor: AppTheme.onPrimary,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildChapterHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
      child: Row(
        children: [
          Text(
            '${_manga.chapterCount} Chapter${_manga.chapterCount == 1 ? '' : 's'}',
            style: const TextStyle(
              color: AppTheme.onBackground,
              fontSize: 16,
              fontWeight: FontWeight.w600,
            ),
          ),
          const Spacer(),
          IconButton(
            icon: Icon(
              _ascending ? Icons.arrow_downward : Icons.arrow_upward,
              size: 20,
              color: AppTheme.primary,
            ),
            tooltip: _ascending ? 'Sort descending' : 'Sort ascending',
            onPressed: () => setState(() => _ascending = !_ascending),
          ),
        ],
      ),
    );
  }

  Widget _buildChapterTile(int chapterIndex) {
    final chapter = _manga.chapters[chapterIndex];
    final isCurrentChapter =
        _progress != null && _progress!.chapterIndex == chapterIndex;

    return InkWell(
      onTap: () => _openReader(chapterIndex),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: AppTheme.surface.withValues(alpha: 0.5),
              width: 1,
            ),
          ),
        ),
        child: Row(
          children: [
            // Chapter info
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    chapter.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: isCurrentChapter
                          ? AppTheme.primary
                          : AppTheme.onBackground,
                      fontSize: 14,
                      fontWeight: isCurrentChapter
                          ? FontWeight.bold
                          : FontWeight.normal,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${chapter.pageCount} page${chapter.pageCount == 1 ? '' : 's'}'
                    '${isCurrentChapter ? '  •  Left off on page ${_progress!.pageIndex + 1}' : ''}',
                    style: const TextStyle(
                      color: AppTheme.textSecondary,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),

            // Reading badge
            if (isCurrentChapter)
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppTheme.primary,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text(
                  'READING',
                  style: TextStyle(
                    color: Colors.black,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.5,
                  ),
                ),
              ),

            // Arrow
            if (!isCurrentChapter)
              const Icon(
                Icons.chevron_right,
                color: AppTheme.textSecondary,
                size: 20,
              ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Cover image widget — loads full cover (not thumbnail) for the banner
// ---------------------------------------------------------------------------

class _CoverImage extends StatefulWidget {
  final MangaItem manga;
  const _CoverImage({required this.manga});

  @override
  State<_CoverImage> createState() => _CoverImageState();
}

class _CoverImageState extends State<_CoverImage> {
  Future<File?>? _thumbFuture;

  @override
  void initState() {
    super.initState();
    _thumbFuture =
        ThumbnailService.instance.getThumbnailFile(widget.manga);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<File?>(
      future: _thumbFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.done &&
            snapshot.data != null) {
          return Image.file(
            snapshot.data!,
            fit: BoxFit.cover,
            width: double.infinity,
          );
        }
        return Container(
          color: AppTheme.surface,
          child: Center(
            child: Icon(
              Icons.menu_book,
              size: 64,
              color: AppTheme.onBackground.withValues(alpha: 0.3),
            ),
          ),
        );
      },
    );
  }
}
