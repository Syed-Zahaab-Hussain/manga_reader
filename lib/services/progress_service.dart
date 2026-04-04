import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as p;
import '../models/reading_progress.dart';
import 'app_preferences.dart';

class ProgressService {
  static const String _fileName = '.manga_reader_progress.json';

  static Future<void> save(ReadingProgress progress) async {
    final file = await _getProgressFile();
    if (file == null) return;

    final entries = await _readStoredEntries(file);
    final storedProgress = await _toStoredProgress(progress);
    if (storedProgress == null) return;

    final existing = entries
        .where((item) =>
            item.mangaId == storedProgress.mangaId &&
            item.chapterIndex == storedProgress.chapterIndex)
        .firstOrNull;
    entries.removeWhere((item) =>
        item.mangaId == storedProgress.mangaId &&
        item.chapterIndex == storedProgress.chapterIndex);
    entries.add(storedProgress.copyWith(
      isCompleted:
          storedProgress.isCompleted || (existing?.isCompleted ?? false),
    ));
    await _writeStoredEntries(file, entries);
  }

  static Future<ReadingProgress?> get(String mangaId) async {
    final map = await getAllAsMap();
    return map[mangaId];
  }

  static Future<Map<int, ReadingProgress>> getForManga(String mangaId) async {
    final file = await _getProgressFile();
    if (file == null || !await file.exists()) return {};

    final rootPath = await AppPreferences.getMangaFolderPath();
    if (rootPath == null || rootPath.isEmpty) return {};

    final relativeId = await _toRelativeId(mangaId);
    if (relativeId == null) return {};

    final entries = await _readStoredEntries(file);
    final result = <int, ReadingProgress>{};
    for (final progress in entries) {
      if (progress.mangaId != relativeId) continue;

      final absoluteProgress = progress.copyWith(
        mangaId: _toAbsoluteId(progress.mangaId, rootPath),
      );
      final existing = result[progress.chapterIndex];
      if (existing != null && existing.lastRead.isAfter(progress.lastRead)) {
        continue;
      }
      result[progress.chapterIndex] = absoluteProgress;
    }
    return result;
  }

  static Future<List<ReadingProgress>> getRecentlyRead({int limit = 8}) async {
    final map = await getAllAsMap();
    final list = map.values.toList()
      ..sort((a, b) => b.lastRead.compareTo(a.lastRead));
    return list.take(limit).toList();
  }

  static Future<void> delete(String mangaId) async {
    final file = await _getProgressFile();
    if (file == null || !await file.exists()) return;

    final relativeId = await _toRelativeId(mangaId);
    if (relativeId == null) return;

    final entries = await _readStoredEntries(file);
    entries.removeWhere((item) => item.mangaId == relativeId);
    await _writeStoredEntries(file, entries);
  }

  static Future<Map<String, ReadingProgress>> getAllAsMap() async {
    final file = await _getProgressFile();
    if (file == null || !await file.exists()) return {};

    final rootPath = await AppPreferences.getMangaFolderPath();
    if (rootPath == null || rootPath.isEmpty) return {};

    final entries = await _readStoredEntries(file);
    final result = <String, ReadingProgress>{};
    for (final progress in entries) {
      final absoluteId = _toAbsoluteId(progress.mangaId, rootPath);
      final existing = result[absoluteId];
      if (existing != null && existing.lastRead.isAfter(progress.lastRead)) {
        continue;
      }
      result[absoluteId] = progress.copyWith(mangaId: absoluteId);
    }
    return result;
  }

  static Future<void> clearAll() async {
    final file = await _getProgressFile();
    if (file == null || !await file.exists()) return;
    await file.delete();
  }

  static Future<File?> _getProgressFile() async {
    final rootPath = await AppPreferences.getMangaFolderPath();
    if (rootPath == null || rootPath.isEmpty) return null;
    return File(p.join(rootPath, _fileName));
  }

  static Future<List<ReadingProgress>> _readStoredEntries(File file) async {
    try {
      if (!await file.exists()) return [];
      final raw = await file.readAsString();
      if (raw.trim().isEmpty) return [];

      final decoded = jsonDecode(raw);
      if (decoded is! List<dynamic>) return [];

      return decoded
          .whereType<Map<String, dynamic>>()
          .map(ReadingProgress.fromJson)
          .toList();
    } catch (_) {
      return [];
    }
  }

  static Future<void> _writeStoredEntries(
    File file,
    List<ReadingProgress> entries,
  ) async {
    final json = jsonEncode(entries.map((item) => item.toJson()).toList());
    await file.writeAsString(json);
  }

  static Future<ReadingProgress?> _toStoredProgress(
    ReadingProgress progress,
  ) async {
    final relativeId = await _toRelativeId(progress.mangaId);
    if (relativeId == null) return null;
    return progress.copyWith(mangaId: relativeId);
  }

  static Future<String?> _toRelativeId(String mangaId) async {
    final rootPath = await AppPreferences.getMangaFolderPath();
    if (rootPath == null || rootPath.isEmpty) return null;
    if (!_isSameOrWithin(rootPath, mangaId)) return null;
    return p.relative(mangaId, from: rootPath);
  }

  static String _toAbsoluteId(String storedId, String rootPath) {
    return p.normalize(p.join(rootPath, storedId));
  }

  static bool _isSameOrWithin(String rootPath, String targetPath) {
    final normalizedRoot = p.normalize(rootPath);
    final normalizedTarget = p.normalize(targetPath);
    return p.equals(normalizedRoot, normalizedTarget) ||
        p.isWithin(normalizedRoot, normalizedTarget);
  }
}
