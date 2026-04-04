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
  static const double _zoomTolerance = 0.02;

  late final MangaItem _manga;
  late int _chapterIndex;
  int _initialPage = 0;

  List<String> _pagePaths = [];
  bool _loading = true;
  int? _zoomedPageIndex;

  // Scroll & position tracking
  final ItemScrollController _scrollController = ItemScrollController();
  final ItemPositionsListener _positionsListener =
      ItemPositionsListener.create();

  int _currentPage = 0;
  Timer? _saveTimer;

  // Top bar visibility
  bool _topBarVisible = true;
  Timer? _hideTimer;

  // Page label visibility
  bool _pageLabelsVisible = true;

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
      _zoomedPageIndex = null;
    });

    final chapter = _manga.chapters[_chapterIndex];
    final paths = await PageLoaderService.instance.loadChapterPages(chapter);

    if (!mounted) return;

    final safePage = jumpToPage.clamp(0, paths.isEmpty ? 0 : paths.length - 1);

    setState(() {
      _pagePaths = paths;
      _currentPage = safePage;
      _loading = false;
      _initialPage = safePage;
    });

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

    final topmost = positions.reduce(
      (a, b) => a.itemLeadingEdge < b.itemLeadingEdge ? a : b,
    );

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
    setState(() => _zoomedPageIndex = null);

    final oldChapter = _manga.chapters[_chapterIndex];
    _chapterIndex = index;

    _loadChapter(jumpToPage: 0);

    PageLoaderService.instance.releaseChapter(oldChapter);
  }

  void _onPageZoomChanged(int pageIndex, bool isZoomed) {
    if (!mounted) return;
    if (isZoomed) {
      if (_zoomedPageIndex != pageIndex) {
        setState(() => _zoomedPageIndex = pageIndex);
      }
      return;
    }

    if (_zoomedPageIndex == pageIndex) {
      setState(() => _zoomedPageIndex = null);
    }
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

    final index = page - 1;
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
          SafeArea(
            top: false,
            child: _buildReaderBody(),
          ),

          if (_topBarVisible) _buildTopBar(),
        ],
      ),
    );
  }

  Widget _buildReaderBody() {
    if (_loading) {
      return const Center(
        child: CircularProgressIndicator(color: AppTheme.primary),
      );
    }

    if (_pagePaths.isEmpty) {
      return const Center(
        child: Text(
          'No pages found',
          style: TextStyle(color: AppTheme.textSecondary, fontSize: 16),
        ),
      );
    }

    return GestureDetector(
      onTap: _toggleTopBar,
      child: ScrollablePositionedList.builder(
        itemScrollController: _scrollController,
        itemPositionsListener: _positionsListener,
        initialScrollIndex: _initialPage,
        itemCount: _pagePaths.length,
        physics: _zoomedPageIndex == null
            ? const AlwaysScrollableScrollPhysics()
            : const NeverScrollableScrollPhysics(),
        addAutomaticKeepAlives: false,
        itemBuilder: (context, index) {
          return _PageWidget(
            key: ValueKey('${_chapterIndex}_$index'),
            filePath: _pagePaths[index],
            pageIndex: index,
            totalPages: _pagePaths.length,
            showLabel: _pageLabelsVisible,
            zoomTolerance: _zoomTolerance,
            onZoomChanged: (isZoomed) =>
                _onPageZoomChanged(index, isZoomed),
          );
        },
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
                IconButton(
                  icon: const Icon(Icons.arrow_back, color: Colors.white),
                  onPressed: () {
                    _saveProgressNow();
                    context.pop();
                  },
                ),

                const SizedBox(width: 4),

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

                // Toggle page labels
                IconButton(
                  icon: Icon(
                    _pageLabelsVisible
                        ? Icons.format_list_numbered
                        : Icons.format_list_numbered_rtl,
                    color: _pageLabelsVisible ? Colors.white : Colors.white38,
                  ),
                  tooltip: _pageLabelsVisible
                      ? 'Hide page numbers'
                      : 'Show page numbers',
                  onPressed: () {
                    setState(() => _pageLabelsVisible = !_pageLabelsVisible);
                    _scheduleHideTopBar();
                  },
                ),

                const SizedBox(width: 4),

                IconButton(
                  icon: const Icon(Icons.skip_previous_rounded,
                      color: Colors.white),
                  onPressed:
                      hasPrev ? () => _goToChapter(_chapterIndex - 1) : null,
                  disabledColor: Colors.white24,
                ),

                IconButton(
                  icon:
                      const Icon(Icons.skip_next_rounded, color: Colors.white),
                  onPressed:
                      hasNext ? () => _goToChapter(_chapterIndex + 1) : null,
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
// Individual page widget — double-tap to zoom in/out
// ---------------------------------------------------------------------------

class _PageWidget extends StatefulWidget {
  final String filePath;
  final int pageIndex;
  final int totalPages;
  final bool showLabel;
  final double zoomTolerance;
  final ValueChanged<bool> onZoomChanged;

  const _PageWidget({
    super.key,
    required this.filePath,
    required this.pageIndex,
    required this.totalPages,
    required this.showLabel,
    required this.zoomTolerance,
    required this.onZoomChanged,
  });

  @override
  State<_PageWidget> createState() => _PageWidgetState();
}

class _PageWidgetState extends State<_PageWidget>
    with SingleTickerProviderStateMixin {
  File? _file;
  bool _exists = false;
  bool _isZoomed = false;

  final TransformationController _transformController =
      TransformationController();
  late final AnimationController _animController;
  Animation<Matrix4>? _animation;

  // Captured on double-tap-down so we know where to zoom into
  Offset _doubleTapPosition = Offset.zero;

  static const double _zoomedScale = 2.5;

  @override
  void initState() {
    super.initState();
    _file = File(widget.filePath);
    _exists = _file!.existsSync();
    _transformController.addListener(_syncZoomState);
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 200),
    )..addListener(() {
        if (_animation != null) {
          _transformController.value = _animation!.value;
        }
      });
  }

  @override
  void dispose() {
    if (_isZoomed) {
      widget.onZoomChanged(false);
    }
    _transformController.removeListener(_syncZoomState);
    _transformController.dispose();
    _animController.dispose();
    super.dispose();
  }

  double get _currentScale => _transformController.value.getMaxScaleOnAxis();

  void _syncZoomState() {
    final isZoomed = _currentScale > 1.0 + widget.zoomTolerance;
    if (isZoomed == _isZoomed) return;
    _isZoomed = isZoomed;
    widget.onZoomChanged(isZoomed);
  }

  void _resetTransform() {
    if (_transformController.value == Matrix4.identity()) return;
    _transformController.value = Matrix4.identity();
  }

  void _onDoubleTapDown(TapDownDetails details) {
    _doubleTapPosition = details.localPosition;
  }

  void _onDoubleTap() {
    final Matrix4 target;

    if (_isZoomed) {
      target = Matrix4.identity();
    } else {
      // Translate so the tapped point stays centred after scaling
      final x = -_doubleTapPosition.dx * (_zoomedScale - 1);
      final y = -_doubleTapPosition.dy * (_zoomedScale - 1);
      target = Matrix4.identity()
        ..translateByDouble(x, y, 0, 1)
        ..scaleByDouble(_zoomedScale, _zoomedScale, 1, 1);
    }

    _animation = Matrix4Tween(
      begin: _transformController.value,
      end: target,
    ).animate(CurvedAnimation(
      parent: _animController,
      curve: Curves.easeInOut,
    ));

    _animController
      ..reset()
      ..forward().whenComplete(() {
        if (!mounted) return;
        if (_currentScale <= 1.0 + widget.zoomTolerance) {
          _resetTransform();
        }
      });
  }

  Widget _buildPageLabel() {
    if (!widget.showLabel) return const SizedBox.shrink();
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

  Widget _buildImage() {
    if (!_exists) {
      return Container(
        height: 400,
        color: Colors.black,
        child: const Center(
          child: Icon(Icons.broken_image, color: Colors.white38, size: 48),
        ),
      );
    }

    return Image.file(
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
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        GestureDetector(
          onDoubleTapDown: _onDoubleTapDown,
          onDoubleTap: _onDoubleTap,
          child: InteractiveViewer(
            transformationController: _transformController,
            minScale: 1.0,
            maxScale: 4.0,
            onInteractionEnd: (_) {
              if (_currentScale <= 1.0 + widget.zoomTolerance) {
                _resetTransform();
              }
            },
            // Only pan when zoomed in; vertical drags scroll between pages otherwise
            panEnabled: _isZoomed,
            child: _buildImage(),
          ),
        ),
        _buildPageLabel(),
      ],
    );
  }
}
