import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:archive/archive_io.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../models/manga_item.dart';

// ---------------------------------------------------------------------------
// ThumbnailService — singleton
// ---------------------------------------------------------------------------

class ThumbnailService {
  ThumbnailService._();
  static final ThumbnailService instance = ThumbnailService._();

  static const int _maxCacheSizeBytes = 500 * 1024 * 1024; // 500 MB
  static const int _thumbnailWidth = 200;

  Directory? _cacheDir;
  File? _metaFile;
  Map<String, _MetaEntry> _meta = {};
  bool _initialized = false;

  // -------------------------------------------------------------------------
  // Initialisation
  // -------------------------------------------------------------------------

  Future<void> _ensureInitialized() async {
    if (_initialized) return;
    final appCache = await getApplicationCacheDirectory();
    _cacheDir = Directory(p.join(appCache.path, 'manga_thumbnails'));
    if (!_cacheDir!.existsSync()) {
      _cacheDir!.createSync(recursive: true);
    }
    _metaFile = File(p.join(_cacheDir!.path, 'meta.json'));
    _meta = _loadMeta();
    _initialized = true;
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  Future<File?> getThumbnailFile(MangaItem manga) async {
    await _ensureInitialized();

    final cacheKey = _cacheKey(manga.id, manga.coverArchiveEntry);
    final thumbFile = _thumbFileForKey(cacheKey);

    // Check cache validity
    if (thumbFile.existsSync() && _meta.containsKey(cacheKey)) {
      final entry = _meta[cacheKey]!;
      // Compare source modification time
      final sourceMtime = _getSourceMtime(
        manga.coverImagePath,
        manga.coverIsInArchive ? manga.path : null,
      );
      if (sourceMtime != null && entry.sourceModified == sourceMtime) {
        // Cache hit — update lastAccessed
        _meta[cacheKey] = _MetaEntry(
          size: entry.size,
          lastAccessed: DateTime.now().millisecondsSinceEpoch,
          sourceModified: entry.sourceModified,
        );
        _saveMeta();
        return thumbFile;
      }
    }

    // Cache miss — generate thumbnail
    final sourceBytes = await _getSourceBytes(manga);
    if (sourceBytes == null) return null;

    return _generateAndSave(cacheKey, thumbFile, sourceBytes, manga.coverImagePath, manga.path);
  }

  Future<File?> getThumbnailFileFromPaths({
    required String sourcePath,
    String? archiveEntry,
    bool isArchive = false,
  }) async {
    await _ensureInitialized();

    final cacheKey = _cacheKey(sourcePath, archiveEntry);
    final thumbFile = _thumbFileForKey(cacheKey);

    if (thumbFile.existsSync() && _meta.containsKey(cacheKey)) {
      final entry = _meta[cacheKey]!;
      final sourceMtime = _getSourceMtime(
        isArchive ? sourcePath : (archiveEntry ?? sourcePath),
        isArchive ? sourcePath : null,
      );
      if (sourceMtime != null && entry.sourceModified == sourceMtime) {
        _meta[cacheKey] = _MetaEntry(
          size: entry.size,
          lastAccessed: DateTime.now().millisecondsSinceEpoch,
          sourceModified: entry.sourceModified,
        );
        _saveMeta();
        return thumbFile;
      }
    }

    Uint8List? sourceBytes;
    if (isArchive && archiveEntry != null) {
      sourceBytes = await _readFromArchive(sourcePath, archiveEntry);
    } else {
      final f = File(sourcePath);
      if (f.existsSync()) {
        sourceBytes = await f.readAsBytes();
      }
    }

    if (sourceBytes == null) return null;

    return _generateAndSave(cacheKey, thumbFile, sourceBytes, sourcePath, isArchive ? sourcePath : null);
  }

  Future<void> clearCache() async {
    await _ensureInitialized();
    if (_cacheDir!.existsSync()) {
      for (final entity in _cacheDir!.listSync()) {
        if (entity is File) {
          try {
            entity.deleteSync();
          } catch (_) {}
        }
      }
    }
    _meta.clear();
    _saveMeta();
  }

  Future<int> getCacheSizeBytes() async {
    await _ensureInitialized();
    int total = 0;
    for (final entry in _meta.values) {
      total += entry.size;
    }
    return total;
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  String _cacheKey(String sourcePath, String? archiveEntry) {
    final combined = '$sourcePath${archiveEntry ?? ''}';
    final hash = combined.hashCode.toRadixString(16);
    final base = p.basenameWithoutExtension(sourcePath);
    final sanitized = base.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
    final keyRaw = '${hash}_$sanitized';
    return keyRaw.length > 32 ? keyRaw.substring(0, 32) : keyRaw;
  }

  File _thumbFileForKey(String key) =>
      File(p.join(_cacheDir!.path, '$key.thumb'));

  int? _getSourceMtime(String? filePath, String? archivePath) {
    final path = archivePath ?? filePath;
    if (path == null) return null;
    try {
      return File(path).statSync().modified.millisecondsSinceEpoch;
    } catch (_) {
      return null;
    }
  }

  Future<Uint8List?> _getSourceBytes(MangaItem manga) async {
    if (manga.coverImagePath == null) return null;

    if (manga.coverIsInArchive && manga.coverArchiveEntry != null) {
      return _readFromArchive(manga.path, manga.coverArchiveEntry!);
    } else {
      final f = File(manga.coverImagePath!);
      if (!f.existsSync()) return null;
      return f.readAsBytes();
    }
  }

  Future<Uint8List?> _readFromArchive(
      String archivePath, String entryName) async {
    try {
      final inputStream = InputFileStream(archivePath);
      final archive = ZipDecoder().decodeStream(inputStream);
      final entry = archive.files
          .where((e) => e.isFile && e.name == entryName)
          .firstOrNull;
      if (entry == null) {
        inputStream.closeSync();
        return null;
      }
      final bytes = entry.content;
      inputStream.closeSync();
      return bytes;
    } catch (_) {
      return null;
    }
  }

  Future<Uint8List?> _generateThumbnail(Uint8List sourceBytes) async {
    try {
      final codec = await ui.instantiateImageCodec(
        sourceBytes,
        targetWidth: _thumbnailWidth,
      );
      final frame = await codec.getNextFrame();
      final byteData =
          await frame.image.toByteData(format: ui.ImageByteFormat.png);
      frame.image.dispose();
      codec.dispose();
      if (byteData == null) return null;
      return byteData.buffer.asUint8List();
    } catch (_) {
      return null;
    }
  }

  Future<File?> _generateAndSave(
    String cacheKey,
    File thumbFile,
    Uint8List sourceBytes,
    String? sourcePath,
    String? archivePath,
  ) async {
    final thumbBytes = await _generateThumbnail(sourceBytes);
    if (thumbBytes == null) return null;

    try {
      await thumbFile.writeAsBytes(thumbBytes);
    } catch (_) {
      return null;
    }

    final sourceMtime = _getSourceMtime(sourcePath, archivePath);

    _meta[cacheKey] = _MetaEntry(
      size: thumbBytes.length,
      lastAccessed: DateTime.now().millisecondsSinceEpoch,
      sourceModified: sourceMtime ?? 0,
    );
    _saveMeta();
    await _evictIfNeeded();
    return thumbFile;
  }

  Future<void> _evictIfNeeded() async {
    int totalSize = 0;
    for (final e in _meta.values) {
      totalSize += e.size;
    }
    if (totalSize <= _maxCacheSizeBytes) return;

    // Sort by lastAccessed ascending (LRU first)
    final sorted = _meta.entries.toList()
      ..sort((a, b) => a.value.lastAccessed.compareTo(b.value.lastAccessed));

    for (final entry in sorted) {
      if (totalSize <= _maxCacheSizeBytes) break;
      final file = _thumbFileForKey(entry.key);
      try {
        if (file.existsSync()) file.deleteSync();
      } catch (_) {}
      totalSize -= entry.value.size;
      _meta.remove(entry.key);
    }
    _saveMeta();
  }

  // -------------------------------------------------------------------------
  // Meta persistence
  // -------------------------------------------------------------------------

  Map<String, _MetaEntry> _loadMeta() {
    if (_metaFile == null || !_metaFile!.existsSync()) return {};
    try {
      final raw = _metaFile!.readAsStringSync();
      final json = jsonDecode(raw) as Map<String, dynamic>;
      return json.map((k, v) =>
          MapEntry(k, _MetaEntry.fromMap(v as Map<String, dynamic>)));
    } catch (_) {
      return {};
    }
  }

  void _saveMeta() {
    if (_metaFile == null) return;
    try {
      final json = _meta.map((k, v) => MapEntry(k, v.toMap()));
      _metaFile!.writeAsStringSync(jsonEncode(json));
    } catch (_) {}
  }
}

// ---------------------------------------------------------------------------
// Meta entry model
// ---------------------------------------------------------------------------

class _MetaEntry {
  final int size;
  final int lastAccessed;
  final int sourceModified;

  const _MetaEntry({
    required this.size,
    required this.lastAccessed,
    required this.sourceModified,
  });

  Map<String, dynamic> toMap() => {
        'size': size,
        'lastAccessed': lastAccessed,
        'sourceModified': sourceModified,
      };

  factory _MetaEntry.fromMap(Map<String, dynamic> map) => _MetaEntry(
        size: map['size'] as int,
        lastAccessed: map['lastAccessed'] as int,
        sourceModified: map['sourceModified'] as int,
      );
}
