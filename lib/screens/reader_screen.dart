import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../models/reading_progress.dart';
import '../services/page_loader_service.dart';
import '../services/progress_service.dart';

class ReaderScreen extends StatefulWidget {
  final Object? extra;
  const ReaderScreen({super.key, this.extra});

  @override
  State<ReaderScreen> createState() => _ReaderScreenState();
}

class _ReaderScreenState extends State<ReaderScreen> {
  late final MangaItem _manga;
  late int _chapterIndex;
  int _initialPage = 0;

  List<String> _pagePaths = [];
  bool _loading = true;

  // Scroll & position tracking
  final ItemScrollController _scrollController = ItemScrollController();
  final ItemPositionsListener _positionsListener =
      ItemPositionsListener.create();

  int _currentPage = 0;
  Timer? _saveTimer;

  // Top bar visibility
  bool _topBarVisible = true;
  Timer? _hideTimer;

  // Zoom
  final TransformationController _zoomController = TransformationController();

  // Track if we already triggered next-chapter preload
  bool _preloadedNext = false;

  @override
  void initState() {
    super.initState();

    final extra = widget.extra as Map<String, dynamic>;
    _manga = extra['manga'] as MangaItem;
    _chapterIndex = extra['chapterIndex'] as int;
    _initialPage = extra['pageIndex'] as int? ?? 0;

    _positionsListener.itemPositions.addListener(_onPositionsChanged);

    _loadChapter(jumpToPage: _initialPage);
    _scheduleHideTopBar();
  }

  @override
  void dispose() {
    _saveProgressNow();
    _saveTimer?.cancel();
    _hideTimer?.cancel();
    _zoomController.dispose();
    _positionsListener.itemPositions.removeListener(_onPositionsChanged);
    PageLoaderService.instance.releaseAll();
    super.dispose();
  }

  // ---------------------------------------------------------------------------
  // Chapter loading
  // ---------------------------------------------------------------------------

  Future<void> _loadChapter({int jumpToPage = 0}) async {
    setState(() {
      _loading = true;
      _preloadedNext = false;
    });

    final chapter = _manga.chapters[_chapterIndex];
    final paths = await PageLoaderService.instance.loadChapterPages(chapter);

    if (!mounted) return;

    // Clamp jumpToPage to valid range
    final safePage = jumpToPage.clamp(0, paths.isEmpty ? 0 : paths.length - 1);

    setState(() {
      _pagePaths = paths;
      _currentPage = safePage;
      _loading = false;
      _initialPage = safePage;
    });

    // Jump after build
    if (safePage > 0) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.isAttached) {
          _scrollController.jumpTo(index: safePage);
        }
      });
    }
  }

  // ---------------------------------------------------------------------------
  // Position tracking & progress saving
  // ---------------------------------------------------------------------------

  void _onPositionsChanged() {
    final positions = _positionsListener.itemPositions.value;
    if (positions.isEmpty) return;

    // The topmost visible item is the current page
    final topmost = positions.reduce(
      (a, b) => a.itemLeadingEdge < b.itemLeadingEdge ? a : b,
    );

    // If the top item is mostly scrolled past, use the next one
    int page = topmost.index;
    if (topmost.itemLeadingEdge < -0.5 && positions.length > 1) {
      final sorted = positions.toList()
        ..sort((a, b) => a.itemLeadingEdge.compareTo(b.itemLeadingEdge));
      if (sorted.length > 1) {
        page = sorted[1].index;
      }
    }

    if (page != _currentPage) {
      setState(() => _currentPage = page);
      _debouncedSave();
    }

    // Preload next chapter when within 5 pages of end
    if (!_preloadedNext &&
        _pagePaths.isNotEmpty &&
        page >= _pagePaths.length - 5 &&
        _chapterIndex < _manga.chapters.length - 1) {
      _preloadedNext = true;
      PageLoaderService.instance
          .preloadChapter(_manga.chapters[_chapterIndex + 1]);
    }
  }

  void _debouncedSave() {
    _saveTimer?.cancel();
    _saveTimer = Timer(const Duration(seconds: 1), _saveProgressNow);
  }

  void _saveProgressNow() {
    _saveTimer?.cancel();
    ProgressService.save(ReadingProgress(
      mangaId: _manga.id,
      mangaTitle: _manga.title,
      coverImagePath: _manga.coverImagePath,
      coverIsInArchive: _manga.coverIsInArchive,
      coverArchiveEntry: _manga.coverArchiveEntry,
      chapterIndex: _chapterIndex,
      pageIndex: _currentPage,
      totalChapters: _manga.chapters.length,
      lastRead: DateTime.now(),
    ));
  }

  // ---------------------------------------------------------------------------
  // Top bar auto-hide
  // ---------------------------------------------------------------------------

  void _scheduleHideTopBar() {
    _hideTimer?.cancel();
    _hideTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) setState(() => _topBarVisible = false);
    });
  }

  void _toggleTopBar() {
    setState(() => _topBarVisible = !_topBarVisible);
    if (_topBarVisible) {
      _scheduleHideTopBar();
    }
  }

  // ---------------------------------------------------------------------------
  // Chapter navigation
  // ---------------------------------------------------------------------------

  void _goToChapter(int index) {
    if (index < 0 || index >= _manga.chapters.length) return;
    _saveProgressNow();

    // Release old chapter's archive temp files if different
    final oldChapter = _manga.chapters[_chapterIndex];
    _chapterIndex = index;

    // Reset zoom
    _zoomController.value = Matrix4.identity();

    _loadChapter(jumpToPage: 0);

    // Release old chapter after loading new one
    PageLoaderService.instance.releaseChapter(oldChapter);
  }

  // ---------------------------------------------------------------------------
  // Page jump modal
  // ---------------------------------------------------------------------------

  void _showPageJumpDialog() {
    final controller = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text('Jump to Page'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          autofocus: true,
          decoration: InputDecoration(
            hintText: '1 — ${_pagePaths.length}',
          ),
          onSubmitted: (value) {
            _jumpToPage(value);
            Navigator.of(ctx).pop();
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              _jumpToPage(controller.text);
              Navigator.of(ctx).pop();
            },
            child: const Text('Go'),
          ),
        ],
      ),
    );
  }

  void _jumpToPage(String input) {
    final page = int.tryParse(input.trim());
    if (page == null || page < 1 || page > _pagePaths.length) return;

    final index = page - 1; // User sees 1-based
    if (_scrollController.isAttached) {
      _scrollController.jumpTo(index: index);
    }
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // Reader content
          if (_loading)
            const Center(
              child: CircularProgressIndicator(color: AppTheme.primary),
            )
          else if (_pagePaths.isEmpty)
            const Center(
              child: Text(
                'No pages found',
                style: TextStyle(color: AppTheme.textSecondary, fontSize: 16),
              ),
            )
          else
            GestureDetector(
              onTap: _toggleTopBar,
              child: InteractiveViewer(
                transformationController: _zoomController,
                minScale: 1.0,
                maxScale: 4.0,
                panEnabled: true,
                child: ScrollablePositionedList.builder(
                  itemScrollController: _scrollController,
                  itemPositionsListener: _positionsListener,
                  initialScrollIndex: _initialPage,
                  itemCount: _pagePaths.length,
                  addAutomaticKeepAlives: false,
                  itemBuilder: (context, index) {
                    return _PageWidget(
                      filePath: _pagePaths[index],
                      pageIndex: index,
                      totalPages: _pagePaths.length,
                    );
                  },
                ),
              ),
            ),

          // Top bar overlay
          if (_topBarVisible) _buildTopBar(),
        ],
      ),
    );
  }

  Widget _buildTopBar() {
    final chapter = _manga.chapters[_chapterIndex];
    final hasPrev = _chapterIndex > 0;
    final hasNext = _chapterIndex < _manga.chapters.length - 1;

    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Colors.black.withValues(alpha: 0.85),
              Colors.transparent,
            ],
          ),
        ),
        child: SafeArea(
          bottom: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
            child: Row(
              children: [
                // Back button
                IconButton(
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                  onPressed: () {
                    _saveProgressNow();
                    context.pop();
                  },
                ),

                const SizedBox(width: 4),

                // Chapter title (truncated)
                Expanded(
                  child: Text(
                    chapter.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),

                // Page indicator (tappable)
                GestureDetector(
                  onTap: _showPageJumpDialog,
                  child: Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Text(
                      '${_currentPage + 1} / ${_pagePaths.length}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),

                const SizedBox(width: 4),

                // Prev chapter
                IconButton(
                  icon: const Icon(Icons.skip_previous_rounded,
                      color: Colors.white),
                  onPressed: hasPrev ? () => _goToChapter(_chapterIndex - 1) : null,
                  disabledColor: Colors.white24,
                ),

                // Next chapter
                IconButton(
                  icon:
                      const Icon(Icons.skip_next_rounded, color: Colors.white),
                  onPressed: hasNext ? () => _goToChapter(_chapterIndex + 1) : null,
                  disabledColor: Colors.white24,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Individual page widget — loads image on demand
// ---------------------------------------------------------------------------

class _PageWidget extends StatefulWidget {
  final String filePath;
  final int pageIndex;
  final int totalPages;

  const _PageWidget({
    required this.filePath,
    required this.pageIndex,
    required this.totalPages,
  });

  @override
  State<_PageWidget> createState() => _PageWidgetState();
}

class _PageWidgetState extends State<_PageWidget> {
  File? _file;
  bool _exists = false;

  @override
  void initState() {
    super.initState();
    _file = File(widget.filePath);
    _exists = _file!.existsSync();
  }

  Widget _buildPageLabel() {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Text(
        '${widget.pageIndex + 1} / ${widget.totalPages}',
        textAlign: TextAlign.center,
        style: const TextStyle(
          color: Colors.white38,
          fontSize: 11,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!_exists) {
      return Column(
        children: [
          Container(
            height: 400,
            color: Colors.black,
            child: const Center(
              child: Icon(Icons.broken_image, color: Colors.white38, size: 48),
            ),
          ),
          _buildPageLabel(),
        ],
      );
    }

    return Column(
      children: [
        Image.file(
          _file!,
          fit: BoxFit.fitWidth,
          width: double.infinity,
          filterQuality: FilterQuality.medium,
          errorBuilder: (context, error, stack) {
            return Container(
              height: 400,
              color: Colors.black,
              child: const Center(
                child: Icon(Icons.broken_image, color: Colors.white38, size: 48),
              ),
            );
          },
          frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
            if (wasSynchronouslyLoaded || frame != null) return child;
            return Container(
              height: 400,
              color: Colors.black,
              child: const Center(
                child: CircularProgressIndicator(
                    color: AppTheme.primary, strokeWidth: 2),
              ),
            );
          },
        ),
        _buildPageLabel(),
      ],
    );
  }
}
