import 'dart:async';
import 'dart:io';
import 'dart:math' show min;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../models/reading_progress.dart';
import '../services/library_cache_service.dart';
import '../services/progress_service.dart';
import '../services/scanner_service.dart';
import '../widgets/manga_card.dart';
import '../widgets/recently_read_section.dart';

// ---------------------------------------------------------------------------
// Sort option enum
// ---------------------------------------------------------------------------

enum SortOption {
  titleAsc,
  titleDesc,
  chaptersDesc,
  chaptersAsc,
}

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

  String _searchQuery = '';
  SortOption _sortOption = SortOption.titleAsc;
  bool _showSearch = false;
  String? _folderPath;

  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadRecentlyRead().then((_) => _loadFolderAndCache());
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
    if (_pendingManga.isEmpty) return;
    // Process in chunks to avoid long sync frames
    const chunkSize = 20;
    final chunk = _pendingManga.sublist(0, min(chunkSize, _pendingManga.length));
    _pendingManga.removeRange(0, chunk.length);

    setState(() {
      for (final m in chunk) {
        m.progress = _progressMap[m.id];
        _allManga.add(m);
      }
      _applyFilter();
    });

    // If more remain, schedule next chunk immediately after frame
    if (_pendingManga.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _flushPending());
    }
  }

  // -------------------------------------------------------------------------
  // Data loading
  // -------------------------------------------------------------------------

  Future<void> _loadFolderAndCache() async {
    final results = await Future.wait([
      SharedPreferences.getInstance(),
      LibraryCacheService.load(),
    ]);
    if (!mounted) return;

    final prefs = results[0] as SharedPreferences;
    final cached = results[1] as List<MangaItem>;
    final path = prefs.getString('manga_folder_path');

    if (cached.isNotEmpty) {
      setState(() {
        _folderPath = path;
        for (final m in cached) {
          m.progress = _progressMap[m.id];
        }
        _allManga = cached;
        _applyFilter();
      });
    } else {
      setState(() => _folderPath = path);
      // No cache yet — scan once to build it
      if (path != null) _startScan(path);
    }
  }

  Future<bool> _requestStoragePermission() async {
    if (!Platform.isAndroid) return true;

    // If already granted, return immediately — no dialog, no pause/resume,
    // so we must NOT set suppressNext (it would skip a legitimate lock later).
    if (await Permission.manageExternalStorage.isGranted) return true;
    if (await Permission.storage.isGranted) return true;

    // Permission is not yet granted — a system dialog will open, which causes
    // app to pause/resume and would trigger the app-lock. Suppress it.
    AppLock.suppressNext = true;
    final manageStatus = await Permission.manageExternalStorage.request();
    if (manageStatus.isGranted) return true;

    AppLock.suppressNext = true;
    final storageStatus = await Permission.storage.request();
    if (storageStatus.isGranted) return true;

    // Both denied — clear the flag so the next resume locks normally.
    AppLock.suppressNext = false;

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text(
              'Storage permission is required to read manga files.'),
          backgroundColor: Colors.red.shade700,
          action: SnackBarAction(
            label: 'Settings',
            onPressed: openAppSettings,
          ),
        ),
      );
    }
    return false;
  }

  Future<void> _loadRecentlyRead() async {
    final results = await Future.wait([
      ProgressService.getRecentlyRead(limit: 8),
      ProgressService.getAllAsMap(),
    ]);
    if (!mounted) return;
    setState(() {
      _recentlyRead = results[0] as List<ReadingProgress>;
      _progressMap = results[1] as Map<String, ReadingProgress>;
    });
  }

  Future<void> _startScan(String path) async {
    if (!await _requestStoragePermission()) return;

    if (!Directory(path).existsSync()) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Manga folder not found: $path'),
          backgroundColor: Colors.red.shade700,
        ),
      );
      return;
    }

    _scanSub?.cancel();
    _scanner.cancel();

    setState(() {
      _allManga = [];
      _filteredManga = [];
      _pendingManga.clear();
      _isScanning = true;
      _scannedCount = 0;
      _totalCount = 0;
    });

    // Flush buffered manga to the UI every 300 ms — one rebuild per tick
    // instead of one rebuild per manga found.
    _batchTimer?.cancel();
    _batchTimer = Timer.periodic(const Duration(milliseconds: 300), (_) {
      if (mounted) _flushPending();
    });

    _scanSub = _scanner.scan(path).listen(
      (event) {
        if (!mounted) return;
        switch (event) {
          case ScanProgressEvent(:final scanned, :final total):
            // Progress bar only — cheap update, no sort needed.
            setState(() {
              _scannedCount = scanned;
              _totalCount = total;
            });
          case ScanMangaEvent(:final manga):
            // Buffer; the timer flushes in batches.
            _pendingManga.add(manga);
          case ScanDoneEvent():
            _batchTimer?.cancel();
            _batchTimer = null;
            _flushPending(); // drain whatever is left
            setState(() => _isScanning = false);
            LibraryCacheService.save(_allManga);
          case ScanErrorEvent(:final message):
            _batchTimer?.cancel();
            _batchTimer = null;
            setState(() => _isScanning = false);
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('Scan error: $message'),
                  backgroundColor: Colors.red.shade700,
                ),
              );
            }
        }
      },
      onError: (Object error) {
        if (!mounted) return;
        _batchTimer?.cancel();
        _batchTimer = null;
        setState(() => _isScanning = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Scan error: $error'),
            backgroundColor: Colors.red.shade700,
          ),
        );
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

    switch (_sortOption) {
      case SortOption.titleAsc:
        list.sort((a, b) => a.title.compareTo(b.title));
      case SortOption.titleDesc:
        list.sort((a, b) => b.title.compareTo(a.title));
      case SortOption.chaptersDesc:
        list.sort((a, b) => b.chapterCount.compareTo(a.chapterCount));
      case SortOption.chaptersAsc:
        list.sort((a, b) => a.chapterCount.compareTo(b.chapterCount));
    }

    _filteredManga = list;
  }

  Future<void> _triggerRefresh() async {
    await _loadRecentlyRead();
    if (_folderPath != null) {
      _startScan(_folderPath!);
    }
  }

  // -------------------------------------------------------------------------
  // Navigation
  // -------------------------------------------------------------------------

  void _onMangaTap(MangaItem manga) {
    context.push('/detail', extra: manga);
  }

  void _onRecentlyReadTap(ReadingProgress progress) {
    final manga = _allManga.where((m) => m.id == progress.mangaId).firstOrNull;
    if (manga != null) {
      context.push('/detail', extra: manga);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Open the library to find this manga'),
        ),
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
        button.localToGlobal(button.size.bottomRight(Offset.zero),
            ancestor: overlay),
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
      ],
    );

    if (selected != null && mounted) {
      setState(() {
        _sortOption = selected;
        _applyFilter();
      });
    }
  }

  PopupMenuItem<SortOption> _buildSortMenuItem(SortOption option, String label) {
    return PopupMenuItem<SortOption>(
      value: option,
      child: Row(
        children: [
          if (_sortOption == option)
            const Icon(Icons.check, size: 16, color: AppTheme.primary)
          else
            const SizedBox(width: 16),
          const SizedBox(width: 8),
          Text(
            label,
            style: const TextStyle(color: AppTheme.onBackground),
          ),
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
            onPressed: () async {
              await context.push('/settings');
              if (!mounted) return;
              _loadRecentlyRead();
              final prefs = await SharedPreferences.getInstance();
              if (!mounted) return;
              final newPath = prefs.getString('manga_folder_path');
              if (newPath != _folderPath) {
                setState(() => _folderPath = newPath);
                LibraryCacheService.clear();
                if (newPath != null) {
                  _startScan(newPath);
                } else {
                  setState(() {
                    _allManga = [];
                    _filteredManga = [];
                    _folderPath = null;
                  });
                }
              }
            },
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
    final progress =
        _totalCount > 0 ? _scannedCount / _totalCount : 0.0;
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
        if (_filteredManga.isEmpty && _searchQuery.isNotEmpty)
          SliverFillRemaining(
            child: Center(
              child: Text(
                'No results for "$_searchQuery"',
                style: const TextStyle(color: AppTheme.textSecondary),
              ),
            ),
          )
        else
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            sliver: SliverGrid(
              delegate: SliverChildBuilderDelegate(
                (context, index) {
                  final manga = _filteredManga[index];
                  return MangaCard(
                    manga: manga,
                    progress: _progressMap[manga.id],
                    onTap: () => _onMangaTap(manga),
                  );
                },
                childCount: _filteredManga.length,
              ),
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
                    onPressed: () => context.push('/settings'),
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
