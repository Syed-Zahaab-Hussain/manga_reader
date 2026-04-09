import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../models/chapter_item.dart';
import '../utils/file_utils.dart';

enum PageLoadErrorType {
  missingSource,
  unreadableSource,
  corruptedArchive,
  noSupportedImages,
}

class PageLoadException implements Exception {
  final PageLoadErrorType type;
  final String message;

  const PageLoadException(this.type, this.message);

  @override
  String toString() => message;
}

/// Provides a list of image file paths for a given chapter.
///
/// - Folder-based chapters: lists and natural-sorts the directory.
/// - Archive-based chapters: extracts images to a temp directory once,
///   then returns paths to the extracted files.
class PageLoaderService {
  PageLoaderService._();
  static final PageLoaderService instance = PageLoaderService._();

  /// Temp directories that have been created for archive extraction.
  /// Key = "${archivePath}::${archiveEntryPrefix}"
  final Map<String, _ExtractedChapter> _extracted = {};

  /// Currently running extractions so we don't double-extract.
  final Map<String, Future<_ExtractedChapter>> _pending = {};

  /// Returns ordered list of image file paths for the given chapter.
  /// For archive chapters, extracts to temp dir first (cached).
  Future<List<String>> loadChapterPages(ChapterItem chapter) async {
    if (!chapter.isArchive) {
      return _loadFolderPages(chapter);
    } else {
      return _loadArchivePages(chapter);
    }
  }

  /// Preload a chapter's pages in the background (archive extraction).
  /// No-op for folder-based chapters.
  Future<void> preloadChapter(ChapterItem chapter) async {
    if (!chapter.isArchive) return;
    await _ensureExtracted(chapter);
  }

  /// Clean up a specific chapter's temp files.
  Future<void> releaseChapter(ChapterItem chapter) async {
    final key = _chapterKey(chapter);
    _pending.remove(key);
    final extracted = _extracted.remove(key);
    if (extracted != null) {
      try {
        if (extracted.tempDir.existsSync()) {
          await extracted.tempDir.delete(recursive: true);
        }
      } catch (_) {}
    }
  }

  /// Clean up all temp files.
  Future<void> releaseAll() async {
    for (final entry in _extracted.values) {
      try {
        if (entry.tempDir.existsSync()) {
          await entry.tempDir.delete(recursive: true);
        }
      } catch (_) {}
    }
    _extracted.clear();
    _pending.clear();
  }

  // -------------------------------------------------------------------------
  // Folder-based
  // -------------------------------------------------------------------------

  Future<List<String>> _loadFolderPages(ChapterItem chapter) async {
    final dir = Directory(chapter.path);
    if (!dir.existsSync()) {
      throw const PageLoadException(
        PageLoadErrorType.missingSource,
        'The chapter folder could not be found. It may have been moved or deleted.',
      );
    }

    final List<File> files;
    try {
      files = dir
          .listSync(followLinks: false)
          .whereType<File>()
          .where((f) => isImageFile(f.path))
          .toList();
    } on FileSystemException {
      throw const PageLoadException(
        PageLoadErrorType.unreadableSource,
        'The chapter folder cannot be read. Check storage permission and try again.',
      );
    }

    if (files.isEmpty) {
      throw const PageLoadException(
        PageLoadErrorType.noSupportedImages,
        'This chapter does not contain any supported images.',
      );
    }

    files.sort(
      (a, b) => naturalCompare(p.basename(a.path), p.basename(b.path)),
    );

    return files.map((f) => f.path).toList();
  }

  // -------------------------------------------------------------------------
  // Archive-based
  // -------------------------------------------------------------------------

  Future<List<String>> _loadArchivePages(ChapterItem chapter) async {
    final extracted = await _ensureExtracted(chapter);
    return extracted.pagePaths;
  }

  Future<_ExtractedChapter> _ensureExtracted(ChapterItem chapter) async {
    final key = _chapterKey(chapter);

    // Already extracted and on disk
    if (_extracted.containsKey(key)) {
      final existing = _extracted[key]!;
      if (existing.tempDir.existsSync()) return existing;
      // Temp dir was deleted — re-extract
      _extracted.remove(key);
    }

    // Already extracting — wait for it
    if (_pending.containsKey(key)) {
      return _pending[key]!;
    }

    // Start extraction
    final future = _extractChapter(chapter, key);
    _pending[key] = future;

    try {
      final result = await future;
      _extracted[key] = result;
      return result;
    } finally {
      _pending.remove(key);
    }
  }

  Future<_ExtractedChapter> _extractChapter(
    ChapterItem chapter,
    String key,
  ) async {
    final cacheDir = await getTemporaryDirectory();
    final hash = key.hashCode.toRadixString(16);
    final tempDir = Directory(p.join(cacheDir.path, 'manga_pages', hash));

    if (!tempDir.existsSync()) {
      tempDir.createSync(recursive: true);
    }

    final archivePath = chapter.path;
    final prefix = chapter.archiveEntryPrefix ?? '';

    InputFileStream? inputStream;
    try {
      if (!File(archivePath).existsSync()) {
        throw const PageLoadException(
          PageLoadErrorType.missingSource,
          'The archive could not be found. It may have been moved or deleted.',
        );
      }
      inputStream = InputFileStream(archivePath);
      final archive = ZipDecoder().decodeStream(inputStream);

      final imageEntries = archive.files.where((e) {
        if (!e.isFile) return false;
        if (!isImageFile(e.name)) return false;
        if (prefix.isNotEmpty && !e.name.startsWith(prefix)) return false;
        // Skip entries that are deeper than one level below the prefix
        final relative = prefix.isNotEmpty
            ? e.name.substring(prefix.length)
            : e.name;
        if (relative.contains('/')) return false;
        return true;
      }).toList();

      imageEntries.sort((a, b) => naturalCompare(a.name, b.name));

      if (imageEntries.isEmpty) {
        throw const PageLoadException(
          PageLoadErrorType.noSupportedImages,
          'This chapter does not contain any supported images.',
        );
      }

      final pagePaths = <String>[];
      for (var i = 0; i < imageEntries.length; i++) {
        final entry = imageEntries[i];
        final ext = p.extension(entry.name);
        final outFile = File(
          p.join(tempDir.path, '${i.toString().padLeft(5, '0')}$ext'),
        );
        await outFile.writeAsBytes(entry.content);
        pagePaths.add(outFile.path);
      }

      return _ExtractedChapter(tempDir: tempDir, pagePaths: pagePaths);
    } on PageLoadException {
      rethrow;
    } on FileSystemException {
      throw const PageLoadException(
        PageLoadErrorType.unreadableSource,
        'The archive cannot be read. Check storage permission and free space.',
      );
    } catch (_) {
      throw const PageLoadException(
        PageLoadErrorType.corruptedArchive,
        'The archive is corrupted, password-protected, or unsupported.',
      );
    } finally {
      inputStream?.closeSync();
    }
  }

  String _chapterKey(ChapterItem chapter) =>
      '${chapter.path}::${chapter.archiveEntryPrefix ?? ''}';
}

class _ExtractedChapter {
  final Directory tempDir;
  final List<String> pagePaths;

  const _ExtractedChapter({required this.tempDir, required this.pagePaths});
}
