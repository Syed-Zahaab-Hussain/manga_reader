import 'dart:async';
import 'dart:io';
import 'dart:math' show min;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../models/reading_progress.dart';
import '../services/app_preferences.dart';
import '../services/library_cache_service.dart';
import '../services/progress_service.dart';
import '../services/scanner_service.dart';
import '../utils/storage_permission.dart';
import '../widgets/manga_card.dart';
import '../widgets/recently_read_section.dart';

// ---------------------------------------------------------------------------
// Sort option enum
// ---------------------------------------------------------------------------

enum SortOption { titleAsc, titleDesc, chaptersDesc, chaptersAsc, lastRead }

enum LibraryStatusFilter { all, unread, reading, completed }

// ---------------------------------------------------------------------------
// LibraryScreen
// ---------------------------------------------------------------------------

class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  final ScannerService _scanner = ScannerService();
  StreamSubscription<ScannerEvent>? _scanSub;

  List<MangaItem> _allManga = [];
  List<MangaItem> _filteredManga = [];
  List<ReadingProgress> _recentlyRead = [];
  Map<String, ReadingProgress> _progressMap = {};

  bool _isScanning = false;
  int _scannedCount = 0;
  int _totalCount = 0;

  final List<MangaItem> _pendingManga = [];
  Timer? _batchTimer;
  int _pendingScanned = 0;
  int _pendingTotal = 0;

  String _searchQuery = '';
  SortOption _sortOption = SortOption.titleAsc;
  LibraryStatusFilter _statusFilter = LibraryStatusFilter.all;
  bool _showSearch = false;
  String? _folderPath;
  String? _libraryError;
  final List<ScanWarningEvent> _scanWarnings = [];

  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadFolderAndCache();
  }

  @override
  void dispose() {
    _batchTimer?.cancel();
    _scanSub?.cancel();
    _scanner.cancel();
    _searchController.dispose();
    super.dispose();
  }

  // -------------------------------------------------------------------------
  // Batch flush
  // -------------------------------------------------------------------------

  void _flushPending() {
    final hasManga = _pendingManga.isNotEmpty;
    final hasProgress =
        _pendingScanned != _scannedCount || _pendingTotal != _totalCount;
    if (!hasManga && !hasProgress) return;

    // Process manga in chunks to avoid long sync frames
    List<MangaItem>? chunk;
    if (hasManga) {
      const chunkSize = 20;
      chunk = _pendingManga.sublist(0, min(chunkSize, _pendingManga.length));
      _pendingManga.removeRange(0, chunk.length);
    }

    setState(() {
      if (hasProgress) {
        _scannedCount = _pendingScanned;
        _totalCount = _pendingTotal;
      }
      if (chunk != null) {
        for (final m in chunk) {
          m.progress = _progressMap[m.id];
          _allManga.add(m);
        }
        // Skip sort while scanning; only apply filter (search) without re-sorting
        if (_isScanning) {
          _filteredManga = _searchQuery.isEmpty
              ? _allManga.toList()
              : _allManga
                    .where(
                      (m) => m.title.toLowerCase().contains(
                        _searchQuery.toLowerCase(),
                      ),
                    )
                    .toList();
        } else {
          _applyFilter();
        }
      }
    });

    // If more manga remain, schedule next chunk immediately after frame
    if (_pendingManga.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _flushPending());
    }
  }

  // -------------------------------------------------------------------------
  // Data loading
  // -------------------------------------------------------------------------

  Future<void> _loadFolderAndCache() async {
    final results = await Future.wait([
      AppPreferences.getMangaFolderPath(),
      LibraryCacheService.load(),
      ProgressService.getAllAsMap(),
    ]);
    if (!mounted) return;

    final path = results[0] as String?;
    final cached = results[1] as List<MangaItem>;
    final progressMap = results[2] as Map<String, ReadingProgress>;

    final allRecent = await ProgressService.getRecentlyRead(limit: 8);
    if (!mounted) return;
    final filtered = _filterRecentForFolder(allRecent, path);

    if (cached.isNotEmpty) {
      setState(() {
        _folderPath = path;
        _progressMap = progressMap;
        _recentlyRead = filtered;
        for (final m in cached) {
          m.progress = _progressMap[m.id];
        }
        _allManga = cached;
        _applyFilter();
      });
    } else {
      setState(() {
        _folderPath = path;
        _progressMap = progressMap;
        _recentlyRead = filtered;
      });
      // No cache yet — scan once to build it
      if (path != null) _startScan(path);
    }
  }

  Future<void> _loadRecentlyRead() async {
    final results = await Future.wait([
      ProgressService.getRecentlyRead(limit: 8),
      ProgressService.getAllAsMap(),
    ]);
    if (!mounted) return;
    final allRecent = results[0] as List<ReadingProgress>;
    final progressMap = results[1] as Map<String, ReadingProgress>;

    setState(() {
      _recentlyRead = _filterRecentForFolder(allRecent, _folderPath);
      _progressMap = progressMap;
      for (final manga in _allManga) {
        manga.progress = progressMap[manga.id];
      }
      _applyFilter();
    });
  }

  Future<void> _startScan(String path) async {
    if (!await requestStoragePermission(context)) return;

    if (!Directory(path).existsSync()) {
      if (!mounted) return;
      setState(() {
        _libraryError =
            'The selected manga folder could not be found. It may have been moved or deleted.';
      });
      return;
    }

    _scanSub?.cancel();
    _scanner.cancel();

    setState(() {
      _allManga = [];
      _filteredManga = [];
      _pendingManga.clear();
      _pendingScanned = 0;
      _pendingTotal = 0;
      _isScanning = true;
      _scannedCount = 0;
      _totalCount = 0;
      _libraryError = null;
      _scanWarnings.clear();
    });

    // Flush buffered manga to the UI every 300 ms — one rebuild per tick
    // instead of one rebuild per manga found.
    _batchTimer?.cancel();
    _batchTimer = Timer.periodic(const Duration(milliseconds: 300), (_) {
      if (mounted) _flushPending();
    });

    _scanSub = _scanner
        .scan(path)
        .listen(
          (event) {
            if (!mounted) return;
            switch (event) {
              case ScanProgressEvent(:final scanned, :final total):
                // Buffer — flushed by the batch timer along with manga items.
                _pendingScanned = scanned;
                _pendingTotal = total;
              case ScanMangaEvent(:final manga):
                // Buffer; the timer flushes in batches.
                _pendingManga.add(manga);
              case ScanWarningEvent():
                _scanWarnings.add(event);
              case ScanDoneEvent():
                _batchTimer?.cancel();
                _batchTimer = null;
                _flushPending(); // drain whatever is left
                setState(() {
                  _isScanning = false;
                  _applyFilter(); // apply sort now that scan is done
                });
                LibraryCacheService.save(_allManga);
                if (_scanWarnings.isNotEmpty) {
                  _showScanWarnings();
                }
              case ScanErrorEvent(:final message):
                _batchTimer?.cancel();
                _batchTimer = null;
                setState(() => _isScanning = false);
                setState(() {
                  _libraryError = 'The library could not be scanned. $message';
                });
            }
          },
          onError: (Object error) {
            if (!mounted) return;
            _batchTimer?.cancel();
            _batchTimer = null;
            setState(() => _isScanning = false);
            setState(() {
              _libraryError = 'The library could not be scanned. $error';
            });
          },
        );
  }

  // -------------------------------------------------------------------------
  // Filter & sort
  // -------------------------------------------------------------------------

  void _applyFilter() {
    var list = _allManga.toList();

    if (_searchQuery.isNotEmpty) {
      final query = _searchQuery.toLowerCase();
      list = list.where((m) => m.title.toLowerCase().contains(query)).toList();
    }

    list = list.where((manga) {
      final progress = _progressMap[manga.id];
      return switch (_statusFilter) {
        LibraryStatusFilter.all => true,
        LibraryStatusFilter.unread => progress == null,
        LibraryStatusFilter.reading =>
          progress != null && !_isMangaCompleted(manga, progress),
        LibraryStatusFilter.completed =>
          progress != null && _isMangaCompleted(manga, progress),
      };
    }).toList();

    switch (_sortOption) {
      case SortOption.titleAsc:
        list.sort((a, b) => a.title.compareTo(b.title));
      case SortOption.titleDesc:
        list.sort((a, b) => b.title.compareTo(a.title));
      case SortOption.chaptersDesc:
        list.sort((a, b) => b.chapterCount.compareTo(a.chapterCount));
      case SortOption.chaptersAsc:
        list.sort((a, b) => a.chapterCount.compareTo(b.chapterCount));
      case SortOption.lastRead:
        list.sort((a, b) {
          final aRead = _progressMap[a.id]?.lastRead;
          final bRead = _progressMap[b.id]?.lastRead;
          if (aRead == null && bRead == null) {
            return a.title.compareTo(b.title);
          }
          if (aRead == null) return 1;
          if (bRead == null) return -1;
          return bRead.compareTo(aRead);
        });
    }

    _filteredManga = list;
  }

  bool _isMangaCompleted(MangaItem manga, ReadingProgress progress) {
    return progress.chapterIndex >= manga.chapters.length - 1 &&
        progress.isCompleted;
  }

  Future<void> _showScanWarnings() async {
    if (!mounted || _scanWarnings.isEmpty) return;
    final warnings = List<ScanWarningEvent>.from(_scanWarnings);
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: Text(
          '${warnings.length} item${warnings.length == 1 ? '' : 's'} skipped',
          style: const TextStyle(color: AppTheme.onBackground),
        ),
        content: SizedBox(
          width: double.maxFinite,
          child: ListView.separated(
            shrinkWrap: true,
            itemCount: warnings.length,
            separatorBuilder: (_, _) => const Divider(),
            itemBuilder: (context, index) {
              final warning = warnings[index];
              return ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(
                  Icons.warning_amber_rounded,
                  color: Colors.amber,
                ),
                title: Text(
                  warning.itemName,
                  style: const TextStyle(color: AppTheme.onBackground),
                ),
                subtitle: Text(
                  warning.message,
                  style: const TextStyle(color: AppTheme.textSecondary),
                ),
              );
            },
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  List<ReadingProgress> _filterRecentForFolder(
    List<ReadingProgress> items,
    String? folderPath,
  ) {
    if (folderPath == null) return <ReadingProgress>[];
    return items.where((p) => p.mangaId.startsWith(folderPath)).toList();
  }

  void _clearLibraryState() {
    setState(() {
      _allManga = [];
      _filteredManga = [];
      _recentlyRead = [];
      _folderPath = null;
    });
  }

  Future<String?> _syncFolderPath() async {
    final newPath = await AppPreferences.getMangaFolderPath();
    if (!mounted) return null;
    if (newPath == _folderPath) return newPath;

    setState(() => _folderPath = newPath);
    await LibraryCacheService.clear();

    if (newPath == null) {
      _clearLibraryState();
    }

    return newPath;
  }

  Future<void> _triggerRefresh() async {
    final path = await _syncFolderPath();
    if (!mounted) return;
    await _loadRecentlyRead();
    if (path != null) {
      _startScan(path);
    }
  }

  Future<void> _openSettingsAndReload() async {
    await context.push('/settings');
    if (!mounted) return;
    await _loadRecentlyRead();
    final previousPath = _folderPath;
    final newPath = await _syncFolderPath();
    if (!mounted) return;
    if (newPath != null && newPath != previousPath) {
      _startScan(newPath);
    }
  }

  // -------------------------------------------------------------------------
  // Navigation
  // -------------------------------------------------------------------------

  Future<void> _onMangaTap(MangaItem manga) async {
    await context.push('/detail', extra: manga);
    if (mounted) _loadRecentlyRead();
  }

  Future<void> _onRecentlyReadTap(ReadingProgress progress) async {
    final manga = _allManga.where((m) => m.id == progress.mangaId).firstOrNull;
    if (manga != null) {
      await context.push('/detail', extra: manga);
      if (mounted) _loadRecentlyRead();
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Open the library to find this manga')),
      );
    }
  }

  // -------------------------------------------------------------------------
  // Sort menu
  // -------------------------------------------------------------------------

  void _showSortMenu(BuildContext context) async {
    final RenderBox button = context.findRenderObject() as RenderBox;
    final RenderBox overlay =
        Overlay.of(context).context.findRenderObject() as RenderBox;
    final position = RelativeRect.fromRect(
      Rect.fromPoints(
        button.localToGlobal(Offset.zero, ancestor: overlay),
        button.localToGlobal(
          button.size.bottomRight(Offset.zero),
          ancestor: overlay,
        ),
      ),
      Offset.zero & overlay.size,
    );

    final selected = await showMenu<SortOption>(
      context: context,
      position: position,
      color: AppTheme.surface,
      items: [
        _buildSortMenuItem(SortOption.titleAsc, 'Title A–Z'),
        _buildSortMenuItem(SortOption.titleDesc, 'Title Z–A'),
        _buildSortMenuItem(SortOption.chaptersDesc, 'Most Chapters'),
        _buildSortMenuItem(SortOption.chaptersAsc, 'Fewest Chapters'),
        _buildSortMenuItem(SortOption.lastRead, 'Last Read'),
      ],
    );

    if (selected != null && mounted) {
      setState(() {
        _sortOption = selected;
        _applyFilter();
      });
    }
  }

  Future<void> _showFilterSheet() async {
    final selected = await showModalBottomSheet<LibraryStatusFilter>(
      context: context,
      backgroundColor: AppTheme.surface,
      showDragHandle: true,
      builder: (context) => SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const ListTile(
              title: Text(
                'Filter library',
                style: TextStyle(
                  color: AppTheme.onBackground,
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            for (final filter in LibraryStatusFilter.values)
              ListTile(
                leading: Icon(
                  filter == _statusFilter
                      ? Icons.radio_button_checked
                      : Icons.radio_button_unchecked,
                  color: filter == _statusFilter
                      ? AppTheme.primary
                      : AppTheme.textSecondary,
                ),
                title: Text(switch (filter) {
                  LibraryStatusFilter.all => 'All',
                  LibraryStatusFilter.unread => 'Unread',
                  LibraryStatusFilter.reading => 'Reading',
                  LibraryStatusFilter.completed => 'Completed',
                }),
                onTap: () => Navigator.of(context).pop(filter),
              ),
          ],
        ),
      ),
    );

    if (selected != null && mounted) {
      setState(() {
        _statusFilter = selected;
        _applyFilter();
      });
    }
  }

  PopupMenuItem<SortOption> _buildSortMenuItem(
    SortOption option,
    String label,
  ) {
    return PopupMenuItem<SortOption>(
      value: option,
      child: Row(
        children: [
          if (_sortOption == option)
            const Icon(Icons.check, size: 16, color: AppTheme.primary)
          else
            const SizedBox(width: 16),
          const SizedBox(width: 8),
          Text(label, style: const TextStyle(color: AppTheme.onBackground)),
        ],
      ),
    );
  }

  // -------------------------------------------------------------------------
  // Build methods
  // -------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: Column(
          children: [
            _buildAppBar(),
            if (_showSearch) _buildSearchBar(),
            if (_isScanning) _buildScanProgress(),
            Expanded(
              child: RefreshIndicator(
                onRefresh: _triggerRefresh,
                color: AppTheme.primary,
                backgroundColor: AppTheme.surface,
                child: _buildContent(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAppBar() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          const Expanded(
            child: Text(
              'Manga Reader',
              style: TextStyle(
                color: AppTheme.onBackground,
                fontSize: 20,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          Builder(
            builder: (ctx) => IconButton(
              icon: const Icon(Icons.sort, color: AppTheme.onBackground),
              tooltip: 'Sort',
              onPressed: () => _showSortMenu(ctx),
            ),
          ),
          IconButton(
            icon: Badge(
              isLabelVisible: _statusFilter != LibraryStatusFilter.all,
              child: const Icon(
                Icons.filter_alt_outlined,
                color: AppTheme.onBackground,
              ),
            ),
            tooltip: 'Filter',
            onPressed: _showFilterSheet,
          ),
          IconButton(
            icon: Icon(
              _showSearch ? Icons.search_off : Icons.search,
              color: AppTheme.onBackground,
            ),
            tooltip: 'Search',
            onPressed: () {
              setState(() {
                _showSearch = !_showSearch;
                if (!_showSearch) {
                  _searchQuery = '';
                  _searchController.clear();
                  _applyFilter();
                }
              });
            },
          ),
          IconButton(
            icon: const Icon(Icons.settings, color: AppTheme.onBackground),
            tooltip: 'Settings',
            onPressed: _openSettingsAndReload,
          ),
        ],
      ),
    );
  }

  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: TextField(
        controller: _searchController,
        autofocus: true,
        style: const TextStyle(color: AppTheme.onBackground),
        decoration: const InputDecoration(
          hintText: 'Search manga...',
          prefixIcon: Icon(Icons.search, color: AppTheme.textSecondary),
        ),
        onChanged: (value) {
          setState(() {
            _searchQuery = value;
            _applyFilter();
          });
        },
      ),
    );
  }

  Widget _buildScanProgress() {
    final progress = _totalCount > 0 ? _scannedCount / _totalCount : 0.0;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  'Scanning... $_scannedCount / $_totalCount',
                  style: const TextStyle(
                    color: AppTheme.textSecondary,
                    fontSize: 12,
                  ),
                ),
              ),
              TextButton(
                onPressed: () {
                  _batchTimer?.cancel();
                  _batchTimer = null;
                  _pendingManga.clear();
                  _scanSub?.cancel();
                  _scanner.cancel();
                  setState(() => _isScanning = false);
                },
                child: const Text(
                  'Cancel',
                  style: TextStyle(color: AppTheme.primary, fontSize: 12),
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          ClipRRect(
            borderRadius: BorderRadius.circular(2),
            child: LinearProgressIndicator(
              value: progress,
              backgroundColor: AppTheme.surface,
              color: AppTheme.primary,
              minHeight: 3,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildContent() {
    if (_folderPath == null) {
      return _buildEmptyState();
    }

    if (_libraryError != null) {
      return _buildLibraryErrorState();
    }

    if (!_isScanning && _allManga.isEmpty) {
      return _buildNoMangaState();
    }

    return CustomScrollView(
      slivers: [
        if (_recentlyRead.isNotEmpty && _searchQuery.isEmpty)
          SliverToBoxAdapter(
            child: RecentlyReadSection(
              items: _recentlyRead,
              onTap: _onRecentlyReadTap,
            ),
          ),
        if (_filteredManga.isEmpty &&
            (_searchQuery.isNotEmpty ||
                _statusFilter != LibraryStatusFilter.all))
          SliverFillRemaining(
            child: Center(
              child: Text(
                _searchQuery.isNotEmpty
                    ? 'No results for "$_searchQuery"'
                    : 'No manga match this filter',
                style: const TextStyle(color: AppTheme.textSecondary),
              ),
            ),
          )
        else
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            sliver: SliverGrid(
              delegate: SliverChildBuilderDelegate((context, index) {
                final manga = _filteredManga[index];
                return MangaCard(
                  manga: manga,
                  progress: _progressMap[manga.id],
                  onTap: () => _onMangaTap(manga),
                );
              }, childCount: _filteredManga.length),
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 180,
                childAspectRatio: 0.6,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildLibraryErrorState() {
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: SizedBox(
          height: constraints.maxHeight,
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.folder_off_outlined,
                    size: 64,
                    color: AppTheme.textSecondary,
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Library unavailable',
                    style: TextStyle(
                      color: AppTheme.onBackground,
                      fontSize: 20,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _libraryError!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: AppTheme.textSecondary),
                  ),
                  const SizedBox(height: 24),
                  Wrap(
                    spacing: 12,
                    runSpacing: 8,
                    children: [
                      OutlinedButton.icon(
                        onPressed: _openSettingsAndReload,
                        icon: const Icon(Icons.folder_open),
                        label: const Text('Choose folder'),
                      ),
                      FilledButton.icon(
                        onPressed: _triggerRefresh,
                        icon: const Icon(Icons.refresh),
                        label: const Text('Try again'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: SizedBox(
          height: constraints.maxHeight,
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.folder_open,
                    size: 64,
                    color: AppTheme.textSecondary,
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'No folder selected\nGo to Settings to pick your manga folder',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: AppTheme.textSecondary,
                      fontSize: 14,
                    ),
                  ),
                  const SizedBox(height: 24),
                  ElevatedButton.icon(
                    onPressed: _openSettingsAndReload,
                    icon: const Icon(Icons.settings),
                    label: const Text('Open Settings'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNoMangaState() {
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: SizedBox(
          height: constraints.maxHeight,
          child: const Center(
            child: Padding(
              padding: EdgeInsets.all(32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.library_books,
                    size: 64,
                    color: AppTheme.textSecondary,
                  ),
                  SizedBox(height: 16),
                  Text(
                    'No manga found in this folder',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: AppTheme.textSecondary,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
