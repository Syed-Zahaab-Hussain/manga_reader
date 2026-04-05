import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../models/reading_progress.dart';
import '../services/progress_service.dart';
import '../widgets/cover_image.dart';

class DetailScreen extends StatefulWidget {
  final Object? extra;
  const DetailScreen({super.key, this.extra});

  @override
  State<DetailScreen> createState() => _DetailScreenState();
}

class _DetailScreenState extends State<DetailScreen> {
  late final MangaItem _manga;
  ReadingProgress? _latestProgress;
  Map<int, ReadingProgress> _chapterProgress = {};
  bool _ascending = true;

  @override
  void initState() {
    super.initState();
    _manga = widget.extra as MangaItem;
    _loadProgress();
  }

  Future<void> _loadProgress() async {
    final latest = await ProgressService.get(_manga.id);
    final chapters = await ProgressService.getForManga(_manga.id);
    if (mounted) {
      setState(() {
        _latestProgress = latest;
        _chapterProgress = chapters;
      });
    }
  }

  List<int> get _sortedIndices {
    final indices = List.generate(_manga.chapters.length, (i) => i);
    if (!_ascending) {
      return indices.reversed.toList();
    }
    return indices;
  }

  void _openReader(int chapterIndex, {int pageIndex = 0}) {
    context.push('/reader', extra: {
      'manga': _manga,
      'chapterIndex': chapterIndex,
      'pageIndex': pageIndex,
    }).then((_) => _loadProgress()); // Reload progress when returning
  }

  void _continueReading() {
    final progress = _latestProgress;
    if (progress == null ||
        progress.chapterIndex < 0 ||
        progress.chapterIndex >= _manga.chapters.length) {
      return;
    }
    _openReader(progress.chapterIndex, pageIndex: progress.pageIndex);
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
          if (_latestProgress != null)
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
          CoverImage(manga: _manga, iconSize: 64),

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
    final p = _latestProgress!;
    if (p.chapterIndex < 0 || p.chapterIndex >= _manga.chapters.length) {
      return const SizedBox.shrink();
    }
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
    final progress = _chapterProgress[chapterIndex];
    final hasProgress = progress != null;

    return InkWell(
      onTap: () => _openReader(
        chapterIndex,
        pageIndex: progress?.pageIndex ?? 0,
      ),
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
                      color: hasProgress
                          ? AppTheme.primary
                          : AppTheme.onBackground,
                      fontSize: 14,
                      fontWeight: hasProgress
                          ? FontWeight.bold
                          : FontWeight.normal,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${chapter.pageCount} page${chapter.pageCount == 1 ? '' : 's'}'
                    '${hasProgress ? '  -  Left off on page ${progress.pageIndex + 1}' : ''}',
                    style: const TextStyle(
                      color: AppTheme.textSecondary,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),

            // Reading badge
            if (hasProgress)
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppTheme.primary,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text(
                  'CONTINUE',
                  style: TextStyle(
                    color: Colors.black,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.5,
                  ),
                ),
              ),

            // Arrow
            if (!hasProgress)
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

