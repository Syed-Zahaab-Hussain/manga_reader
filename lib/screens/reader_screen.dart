import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../models/reading_progress.dart';
import '../services/app_preferences.dart';
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

  List<_ReaderPageData> _pages = [];
  bool _loading = true;
  PageLoadException? _loadError;
  int? _zoomedPageIndex;
  bool _zoomEnabled = false;

  final ItemScrollController _scrollController = ItemScrollController();
  final ItemPositionsListener _positionsListener =
      ItemPositionsListener.create();
  PageController? _pageController;

  int _currentPage = 0;
  Timer? _saveTimer;

  bool _topBarVisible = true;
  Timer? _hideTimer;

  bool _pageLabelsVisible = true;
  bool _preloadedNext = false;
  Offset? _tapDownPosition;
  ReadingDirection _readingDirection = ReadingDirection.vertical;
  HorizontalPageFit _horizontalPageFit = HorizontalPageFit.width;
  double _imageWidth = 1.0;

  bool get _isHorizontal => _readingDirection != ReadingDirection.vertical;

  bool get _fitHorizontalWidth => _horizontalPageFit == HorizontalPageFit.width;

  @override
  void initState() {
    super.initState();

    final extra = widget.extra as Map<String, dynamic>;
    _manga = extra['manga'] as MangaItem;
    _chapterIndex = extra['chapterIndex'] as int;
    _initialPage = extra['pageIndex'] as int? ?? 0;

    _positionsListener.itemPositions.addListener(_onPositionsChanged);

    _syncSystemUi();
    _loadReader();
    _scheduleHideTopBar();
  }

  @override
  void dispose() {
    _restoreSystemUi();
    _saveProgressNow();
    _pageController?.dispose();
    _saveTimer?.cancel();
    _hideTimer?.cancel();
    _positionsListener.itemPositions.removeListener(_onPositionsChanged);
    PageLoaderService.instance.releaseAll();
    super.dispose();
  }

  Future<void> _loadReader() async {
    final results = await Future.wait([
      AppPreferences.getReadingDirection(),
      AppPreferences.getHorizontalPageFit(),
      AppPreferences.getReaderImageWidth(),
    ]);
    if (!mounted) return;
    setState(() {
      _readingDirection = results[0] as ReadingDirection;
      _horizontalPageFit = results[1] as HorizontalPageFit;
      _imageWidth = results[2] as double;
    });
    await _loadChapter(jumpToPage: _initialPage);
  }

  Future<void> _loadChapter({int jumpToPage = 0}) async {
    setState(() {
      _loading = true;
      _loadError = null;
      _preloadedNext = false;
      _zoomedPageIndex = null;
    });

    final chapter = _manga.chapters[_chapterIndex];
    late final List<_ReaderPageData> pages;
    try {
      final paths = await PageLoaderService.instance.loadChapterPages(chapter);
      pages = await Future.wait(paths.map(_buildPageData));
    } on PageLoadException catch (error) {
      if (!mounted) return;
      setState(() {
        _pages = [];
        _loading = false;
        _loadError = error;
      });
      return;
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _pages = [];
        _loading = false;
        _loadError = const PageLoadException(
          PageLoadErrorType.unreadableSource,
          'This chapter could not be opened. Check the source files and try again.',
        );
      });
      return;
    }

    if (!mounted) return;

    final safePage = jumpToPage.clamp(0, pages.isEmpty ? 0 : pages.length - 1);

    final oldPageController = _pageController;
    final newPageController = PageController(initialPage: safePage);

    setState(() {
      _pages = pages;
      _currentPage = safePage;
      _loading = false;
      _initialPage = safePage;
      _pageController = newPageController;
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      oldPageController?.dispose();
    });

    if (safePage > 0) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.isAttached) {
          _scrollController.jumpTo(index: safePage);
        }
      });
    }
  }

  Future<_ReaderPageData> _buildPageData(String filePath) async {
    final size = await _readImageSize(filePath);
    final aspectRatio = switch (size) {
      Size(width: final width, height: final height)
          when width > 0 && height > 0 =>
        width / height,
      _ => 0.7,
    };
    return _ReaderPageData(filePath: filePath, aspectRatio: aspectRatio);
  }

  Future<Size?> _readImageSize(String filePath) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) return null;

      final bytes = await file.readAsBytes();
      if (bytes.isEmpty) return null;

      final buffer = await ui.ImmutableBuffer.fromUint8List(bytes);
      final descriptor = await ui.ImageDescriptor.encoded(buffer);
      try {
        return Size(descriptor.width.toDouble(), descriptor.height.toDouble());
      } finally {
        descriptor.dispose();
        buffer.dispose();
      }
    } catch (_) {
      return null;
    }
  }

  void _onPositionsChanged() {
    if (_isHorizontal) return;
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
        _pages.isNotEmpty &&
        page >= _pages.length - 5 &&
        _chapterIndex < _manga.chapters.length - 1) {
      _preloadedNext = true;
      PageLoaderService.instance.preloadChapter(
        _manga.chapters[_chapterIndex + 1],
      );
    }
  }

  void _debouncedSave() {
    _saveTimer?.cancel();
    _saveTimer = Timer(const Duration(seconds: 1), _saveProgressNow);
  }

  void _saveProgressNow() {
    _saveTimer?.cancel();
    ProgressService.save(
      ReadingProgress(
        mangaId: _manga.id,
        mangaTitle: _manga.title,
        coverImagePath: _manga.coverImagePath,
        coverIsInArchive: _manga.coverIsInArchive,
        coverArchiveEntry: _manga.coverArchiveEntry,
        chapterIndex: _chapterIndex,
        pageIndex: _currentPage,
        totalChapters: _manga.chapters.length,
        isCompleted: _pages.isNotEmpty && _currentPage >= _pages.length - 1,
        lastRead: DateTime.now(),
      ),
    );
  }

  void _scheduleHideTopBar() {
    _hideTimer?.cancel();
    _hideTimer = Timer(const Duration(seconds: 3), () {
      if (!mounted) return;
      setState(() => _topBarVisible = false);
      _syncSystemUi();
    });
  }

  void _toggleTopBar() {
    setState(() => _topBarVisible = !_topBarVisible);
    _syncSystemUi();
    if (_topBarVisible) {
      _scheduleHideTopBar();
    }
  }

  void _syncSystemUi() {
    if (_topBarVisible) {
      SystemChrome.setEnabledSystemUIMode(
        SystemUiMode.manual,
        overlays: SystemUiOverlay.values,
      );
      SystemChrome.setSystemUIOverlayStyle(
        const SystemUiOverlayStyle(
          statusBarColor: Colors.black,
          statusBarIconBrightness: Brightness.light,
          statusBarBrightness: Brightness.dark,
        ),
      );
      return;
    }

    SystemChrome.setEnabledSystemUIMode(
      SystemUiMode.manual,
      overlays: [SystemUiOverlay.bottom],
    );
  }

  void _restoreSystemUi() {
    SystemChrome.setEnabledSystemUIMode(
      SystemUiMode.manual,
      overlays: SystemUiOverlay.values,
    );
    SystemChrome.setSystemUIOverlayStyle(
      const SystemUiOverlayStyle(statusBarColor: Colors.transparent),
    );
  }

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

  Future<void> _showImageWidthDialog() async {
    var width = _imageWidth;
    final controller = TextEditingController(
      text: (width * 100).round().toString(),
    );

    final selected = await showModalBottomSheet<double>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.surface,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setSheetState) {
            void updateWidth(double value) {
              final clamped = value.clamp(40.0, 100.0).toDouble();
              setSheetState(() => width = clamped / 100);
              controller.text = clamped.round().toString();
              controller.selection = TextSelection.collapsed(
                offset: controller.text.length,
              );
            }

            return AnimatedPadding(
              duration: const Duration(milliseconds: 160),
              padding: EdgeInsets.only(
                bottom: MediaQuery.viewInsetsOf(ctx).bottom,
              ),
              child: SafeArea(
                top: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Image width',
                        style: TextStyle(
                          color: AppTheme.onBackground,
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: Slider(
                              value: width * 100,
                              min: 40,
                              max: 100,
                              divisions: 60,
                              label: '${(width * 100).round()}%',
                              onChanged: updateWidth,
                            ),
                          ),
                          SizedBox(
                            width: 76,
                            child: TextField(
                              controller: controller,
                              keyboardType: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                              ],
                              style: const TextStyle(
                                color: AppTheme.onBackground,
                              ),
                              decoration: const InputDecoration(
                                suffixText: '%',
                                isDense: true,
                              ),
                              onChanged: (value) {
                                final percent = double.tryParse(value);
                                if (percent != null &&
                                    percent >= 40 &&
                                    percent <= 100) {
                                  setSheetState(() => width = percent / 100);
                                }
                              },
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.end,
                        children: [
                          TextButton(
                            onPressed: () => updateWidth(100),
                            child: const Text('Reset'),
                          ),
                          const SizedBox(width: 8),
                          FilledButton(
                            onPressed: () => Navigator.of(ctx).pop(width),
                            child: const Text('Apply'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            );
          },
        );
      },
    );
    controller.dispose();

    if (selected == null || !mounted) return;
    await AppPreferences.setReaderImageWidth(selected);
    if (!mounted) return;
    setState(() => _imageWidth = selected);
    _scheduleHideTopBar();
  }

  Future<void> _showReaderSettings() async {
    _hideTimer?.cancel();

    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppTheme.surface,
      showDragHandle: true,
      builder: (sheetContext) {
        return StatefulBuilder(
          builder: (context, setSheetState) {
            void togglePageLabels(bool value) {
              setState(() => _pageLabelsVisible = value);
              setSheetState(() {});
            }

            void toggleZoom(bool value) {
              setState(() {
                _zoomEnabled = value;
                if (!value) {
                  _zoomedPageIndex = null;
                }
              });
              setSheetState(() {});
            }

            return SafeArea(
              top: false,
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(12, 0, 12, 16),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Padding(
                      padding: EdgeInsets.fromLTRB(12, 0, 12, 8),
                      child: Text(
                        'Reader settings',
                        style: TextStyle(
                          color: AppTheme.onBackground,
                          fontSize: 20,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    SwitchListTile(
                      secondary: const Icon(Icons.zoom_in),
                      title: const Text('Zoom'),
                      subtitle: Text(
                        _zoomEnabled
                            ? 'Pinch and pan enabled'
                            : 'Natural page scrolling enabled',
                      ),
                      value: _zoomEnabled,
                      onChanged: toggleZoom,
                    ),
                    SwitchListTile(
                      secondary: const Icon(Icons.format_list_numbered),
                      title: const Text('Page numbers'),
                      value: _pageLabelsVisible,
                      onChanged: togglePageLabels,
                    ),
                    ListTile(
                      leading: Icon(
                        _isHorizontal
                            ? Icons.view_agenda_outlined
                            : Icons.swipe_outlined,
                      ),
                      title: const Text('Reading mode'),
                      subtitle: Text(
                        _isHorizontal
                            ? 'Horizontal pages'
                            : 'Vertical scrolling',
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () {
                        Navigator.of(sheetContext).pop();
                        unawaited(_toggleReadingDirection());
                      },
                    ),
                    if (_isHorizontal)
                      ListTile(
                        leading: Icon(
                          _fitHorizontalWidth
                              ? Icons.fit_screen
                              : Icons.fullscreen,
                        ),
                        title: const Text('Page fit'),
                        subtitle: Text(
                          _fitHorizontalWidth
                              ? 'Fit page width'
                              : 'Fit whole page',
                        ),
                        trailing: const Icon(Icons.swap_horiz),
                        onTap: () {
                          Navigator.of(sheetContext).pop();
                          unawaited(_toggleHorizontalPageFit());
                        },
                      ),
                    ListTile(
                      leading: const Icon(Icons.width_normal),
                      title: const Text('Image width'),
                      subtitle: Text(
                        '${(_imageWidth * 100).round()}% of screen',
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () {
                        Navigator.of(sheetContext).pop();
                        WidgetsBinding.instance.addPostFrameCallback((_) {
                          if (mounted) {
                            unawaited(_showImageWidthDialog());
                          }
                        });
                      },
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );

    if (mounted) {
      _scheduleHideTopBar();
    }
  }

  Future<void> _toggleReadingDirection() async {
    final nextDirection = _readingDirection == ReadingDirection.vertical
        ? ReadingDirection.leftToRight
        : ReadingDirection.vertical;
    await AppPreferences.setReadingDirection(nextDirection);
    if (!mounted) return;

    final oldPageController = _pageController;
    final newPageController = PageController(initialPage: _currentPage);

    setState(() {
      _readingDirection = nextDirection;
      _zoomedPageIndex = null;
      _initialPage = _currentPage;
      _pageController = newPageController;
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      oldPageController?.dispose();
    });

    if (nextDirection == ReadingDirection.vertical) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.isAttached) {
          _scrollController.jumpTo(index: _currentPage);
        }
      });
    }
    _scheduleHideTopBar();
  }

  Future<void> _toggleHorizontalPageFit() async {
    final nextFit = _horizontalPageFit == HorizontalPageFit.width
        ? HorizontalPageFit.page
        : HorizontalPageFit.width;
    await AppPreferences.setHorizontalPageFit(nextFit);
    if (!mounted) return;

    final oldPageController = _pageController;
    final newPageController = PageController(initialPage: _currentPage);

    setState(() {
      _horizontalPageFit = nextFit;
      _zoomedPageIndex = null;
      _pageController = newPageController;
    });

    WidgetsBinding.instance.addPostFrameCallback((_) {
      oldPageController?.dispose();
    });
    _scheduleHideTopBar();
  }

  void _showPageJumpDialog() {
    final controller = TextEditingController(text: '${_currentPage + 1}');
    controller.selection = TextSelection(
      baseOffset: 0,
      extentOffset: controller.text.length,
    );

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.surface,
      builder: (ctx) {
        void submit(String value) {
          _jumpToPage(value);
          Navigator.of(ctx).pop();
        }

        return AnimatedPadding(
          duration: const Duration(milliseconds: 160),
          curve: Curves.easeOut,
          padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(ctx).bottom),
          child: SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 16),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: controller,
                      autofocus: true,
                      keyboardType: TextInputType.number,
                      textInputAction: TextInputAction.go,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      style: const TextStyle(color: AppTheme.onBackground),
                      decoration: InputDecoration(
                        labelText: 'Jump to page',
                        hintText: '1 - ${_pages.length}',
                      ),
                      onSubmitted: submit,
                    ),
                  ),
                  const SizedBox(width: 12),
                  FilledButton(
                    onPressed: () => submit(controller.text),
                    child: const Text('Go'),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  void _jumpToPage(String input) {
    final page = int.tryParse(input.trim());
    if (page == null || page < 1 || page > _pages.length) return;

    final index = page - 1;
    if (_isHorizontal) {
      _pageController?.jumpToPage(index);
    } else if (_scrollController.isAttached) {
      _scrollController.jumpTo(index: index);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          SafeArea(top: false, child: _buildReaderBody()),
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

    if (_loadError != null) {
      return _buildLoadError(_loadError!);
    }

    if (_pages.isEmpty) {
      return const Center(
        child: Text(
          'No pages found',
          style: TextStyle(color: AppTheme.textSecondary, fontSize: 16),
        ),
      );
    }

    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onTapDown: (details) => _tapDownPosition = details.localPosition,
      onTapUp: _handleReaderTapUp,
      onTapCancel: () => _tapDownPosition = null,
      child: _isHorizontal ? _buildHorizontalPages() : _buildVerticalPages(),
    );
  }

  Widget _buildLoadError(PageLoadException error) {
    final icon = switch (error.type) {
      PageLoadErrorType.missingSource => Icons.folder_off_outlined,
      PageLoadErrorType.unreadableSource => Icons.lock_outline,
      PageLoadErrorType.corruptedArchive => Icons.archive_outlined,
      PageLoadErrorType.noSupportedImages => Icons.image_not_supported_outlined,
    };
    final title = switch (error.type) {
      PageLoadErrorType.missingSource => 'Source not found',
      PageLoadErrorType.unreadableSource => 'Cannot read chapter',
      PageLoadErrorType.corruptedArchive => 'Archive cannot be opened',
      PageLoadErrorType.noSupportedImages => 'No supported pages',
    };

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: Colors.white38, size: 64),
            const SizedBox(height: 16),
            Text(
              title,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              error.message,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: AppTheme.textSecondary,
                fontSize: 14,
              ),
            ),
            const SizedBox(height: 24),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: 12,
              runSpacing: 8,
              children: [
                OutlinedButton(
                  onPressed: () => context.pop(),
                  child: const Text('Go back'),
                ),
                FilledButton.icon(
                  onPressed: () => _loadChapter(jumpToPage: _currentPage),
                  icon: const Icon(Icons.refresh),
                  label: const Text('Try again'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildVerticalPages() {
    return ScrollablePositionedList.builder(
      itemScrollController: _scrollController,
      itemPositionsListener: _positionsListener,
      initialScrollIndex: _initialPage,
      itemCount: _pages.length,
      physics: _zoomedPageIndex == null
          ? const AlwaysScrollableScrollPhysics()
          : const NeverScrollableScrollPhysics(),
      addAutomaticKeepAlives: false,
      itemBuilder: (context, index) {
        final page = _pages[index];
        return _PageWidget(
          key: ValueKey('${_chapterIndex}_$index'),
          filePath: page.filePath,
          aspectRatio: page.aspectRatio,
          pageIndex: index,
          totalPages: _pages.length,
          showLabel: _pageLabelsVisible,
          zoomEnabled: _zoomEnabled,
          zoomTolerance: _zoomTolerance,
          targetWidth: MediaQuery.sizeOf(context).width * _imageWidth,
          fitToScreen: false,
          allowPageScroll: false,
          onZoomChanged: (isZoomed) => _onPageZoomChanged(index, isZoomed),
        );
      },
    );
  }

  Widget _buildHorizontalPages() {
    return PageView.builder(
      controller: _pageController,
      physics: _zoomedPageIndex == null
          ? const PageScrollPhysics()
          : const NeverScrollableScrollPhysics(),
      itemCount: _pages.length,
      onPageChanged: _onHorizontalPageChanged,
      itemBuilder: (context, index) {
        final page = _pages[index];
        return SizedBox.expand(
          child: _PageWidget(
            key: ValueKey('${_chapterIndex}_$index'),
            filePath: page.filePath,
            aspectRatio: page.aspectRatio,
            pageIndex: index,
            totalPages: _pages.length,
            showLabel: _pageLabelsVisible,
            zoomEnabled: _zoomEnabled,
            zoomTolerance: _zoomTolerance,
            targetWidth: MediaQuery.sizeOf(context).width * _imageWidth,
            fitToScreen: !_fitHorizontalWidth,
            allowPageScroll: _fitHorizontalWidth,
            onZoomChanged: (isZoomed) => _onPageZoomChanged(index, isZoomed),
          ),
        );
      },
    );
  }

  void _onHorizontalPageChanged(int page) {
    if (page != _currentPage) {
      setState(() => _currentPage = page);
      _debouncedSave();
    }

    if (!_preloadedNext &&
        _pages.isNotEmpty &&
        page >= _pages.length - 2 &&
        _chapterIndex < _manga.chapters.length - 1) {
      _preloadedNext = true;
      PageLoaderService.instance.preloadChapter(
        _manga.chapters[_chapterIndex + 1],
      );
    }
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
        color: Colors.black.withValues(alpha: 0.92),
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
                GestureDetector(
                  onTap: _showPageJumpDialog,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Text(
                      '${_currentPage + 1} / ${_pages.length}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.tune_rounded, color: Colors.white),
                  tooltip: 'Reader settings',
                  onPressed: _showReaderSettings,
                ),
                const SizedBox(width: 4),
                IconButton(
                  icon: const Icon(
                    Icons.skip_previous_rounded,
                    color: Colors.white,
                  ),
                  onPressed: hasPrev
                      ? () => _goToChapter(_chapterIndex - 1)
                      : null,
                  disabledColor: Colors.white24,
                ),
                IconButton(
                  icon: const Icon(
                    Icons.skip_next_rounded,
                    color: Colors.white,
                  ),
                  onPressed: hasNext
                      ? () => _goToChapter(_chapterIndex + 1)
                      : null,
                  disabledColor: Colors.white24,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _handleReaderTapUp(TapUpDetails details) {
    final start = _tapDownPosition;
    _tapDownPosition = null;
    if (start == null) return;

    final movement = (details.localPosition - start).distance;
    if (movement > 8) return;

    _toggleTopBar();
  }
}

class _ReaderPageData {
  final String filePath;
  final double aspectRatio;

  const _ReaderPageData({required this.filePath, required this.aspectRatio});
}

class _PageWidget extends StatefulWidget {
  final String filePath;
  final double aspectRatio;
  final int pageIndex;
  final int totalPages;
  final bool showLabel;
  final bool zoomEnabled;
  final double zoomTolerance;
  final double targetWidth;
  final bool fitToScreen;
  final bool allowPageScroll;
  final ValueChanged<bool> onZoomChanged;

  const _PageWidget({
    super.key,
    required this.filePath,
    required this.aspectRatio,
    required this.pageIndex,
    required this.totalPages,
    required this.showLabel,
    required this.zoomEnabled,
    required this.zoomTolerance,
    required this.targetWidth,
    required this.fitToScreen,
    required this.allowPageScroll,
    required this.onZoomChanged,
  });

  @override
  State<_PageWidget> createState() => _PageWidgetState();
}

class _PageWidgetState extends State<_PageWidget> {
  File? _file;
  bool _exists = false;
  bool _isZoomed = false;

  final TransformationController _transformController =
      TransformationController();

  @override
  void initState() {
    super.initState();
    _file = File(widget.filePath);
    _exists = _file!.existsSync();
    _transformController.addListener(_syncZoomState);
  }

  @override
  void didUpdateWidget(covariant _PageWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.zoomEnabled && !widget.zoomEnabled) {
      _resetTransform();
    }
  }

  @override
  void dispose() {
    if (_isZoomed) {
      widget.onZoomChanged(false);
    }
    _transformController.removeListener(_syncZoomState);
    _transformController.dispose();
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

  Widget _buildPageLabel() {
    if (!widget.showLabel) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Text(
        '${widget.pageIndex + 1} / ${widget.totalPages}',
        textAlign: TextAlign.center,
        style: const TextStyle(color: Colors.white38, fontSize: 11),
      ),
    );
  }

  Widget _buildImage() {
    if (!_exists) {
      return Container(
        color: Colors.black,
        alignment: Alignment.center,
        child: const Icon(Icons.broken_image, color: Colors.white38, size: 48),
      );
    }

    final cacheWidth =
        (widget.targetWidth * MediaQuery.devicePixelRatioOf(context))
            .round()
            .clamp(1, 4096)
            .toInt();

    final image = Image.file(
      _file!,
      fit: BoxFit.fill,
      width: double.infinity,
      cacheWidth: cacheWidth,
      filterQuality: FilterQuality.low,
      gaplessPlayback: true,
      errorBuilder: (context, error, stack) {
        return Container(
          color: Colors.black,
          alignment: Alignment.center,
          child: const Icon(
            Icons.broken_image,
            color: Colors.white38,
            size: 48,
          ),
        );
      },
    );

    if (!widget.fitToScreen) {
      return Center(
        child: RepaintBoundary(
          child: SizedBox(
            width: widget.targetWidth,
            child: AspectRatio(aspectRatio: widget.aspectRatio, child: image),
          ),
        ),
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final maxWidth = constraints.maxWidth < widget.targetWidth
            ? constraints.maxWidth
            : widget.targetWidth;
        final maxHeight = constraints.maxHeight.isFinite
            ? constraints.maxHeight
            : MediaQuery.sizeOf(context).height;
        final widthFromHeight = maxHeight * widget.aspectRatio;
        final width = widthFromHeight < maxWidth ? widthFromHeight : maxWidth;
        final height = width / widget.aspectRatio;

        return Center(
          child: RepaintBoundary(
            child: SizedBox(width: width, height: height, child: image),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.zoomEnabled) {
      if (widget.allowPageScroll) {
        return SingleChildScrollView(
          physics: const ClampingScrollPhysics(),
          child: Column(children: [_buildImage(), _buildPageLabel()]),
        );
      }

      if (widget.fitToScreen) {
        return Column(
          children: [
            Expanded(child: _buildImage()),
            _buildPageLabel(),
          ],
        );
      }

      return Column(children: [_buildImage(), _buildPageLabel()]);
    }

    if (widget.allowPageScroll) {
      return InteractiveViewer(
        transformationController: _transformController,
        minScale: 1.0,
        maxScale: 4.0,
        onInteractionEnd: (_) {
          if (_currentScale <= 1.0 + widget.zoomTolerance) {
            _resetTransform();
          }
        },
        panEnabled: _isZoomed,
        child: SingleChildScrollView(
          physics: _isZoomed
              ? const NeverScrollableScrollPhysics()
              : const ClampingScrollPhysics(),
          child: Column(children: [_buildImage(), _buildPageLabel()]),
        ),
      );
    }

    if (widget.fitToScreen) {
      return Column(
        children: [
          Expanded(
            child: InteractiveViewer(
              transformationController: _transformController,
              minScale: 1.0,
              maxScale: 4.0,
              onInteractionEnd: (_) {
                if (_currentScale <= 1.0 + widget.zoomTolerance) {
                  _resetTransform();
                }
              },
              panEnabled: _isZoomed,
              child: _buildImage(),
            ),
          ),
          _buildPageLabel(),
        ],
      );
    }

    return Column(
      children: [
        InteractiveViewer(
          transformationController: _transformController,
          minScale: 1.0,
          maxScale: 4.0,
          onInteractionEnd: (_) {
            if (_currentScale <= 1.0 + widget.zoomTolerance) {
              _resetTransform();
            }
          },
          panEnabled: _isZoomed,
          child: _buildImage(),
        ),
        _buildPageLabel(),
      ],
    );
  }
}
