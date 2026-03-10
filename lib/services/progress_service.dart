import 'package:shared_preferences/shared_preferences.dart';
import '../models/reading_progress.dart';

class ProgressService {
  static const String _prefix = 'progress:';

  static Future<void> save(ReadingProgress progress) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefix + progress.mangaId, progress.toJsonString());
  }

  static Future<ReadingProgress?> get(String mangaId) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_prefix + mangaId);
    if (raw == null) return null;
    try {
      return ReadingProgress.fromJsonString(raw);
    } catch (_) {
      return null;
    }
  }

  static Future<List<ReadingProgress>> getRecentlyRead({int limit = 8}) async {
    final map = await getAllAsMap();
    final list = map.values.toList()
      ..sort((a, b) => b.lastRead.compareTo(a.lastRead));
    return list.take(limit).toList();
  }

  static Future<void> delete(String mangaId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_prefix + mangaId);
  }

  static Future<Map<String, ReadingProgress>> getAllAsMap() async {
    final prefs = await SharedPreferences.getInstance();
    final keys = prefs.getKeys().where((k) => k.startsWith(_prefix));
    final result = <String, ReadingProgress>{};
    for (final key in keys) {
      final raw = prefs.getString(key);
      if (raw == null) continue;
      try {
        final progress = ReadingProgress.fromJsonString(raw);
        result[progress.mangaId] = progress;
      } catch (_) {
        // skip corrupted entries
      }
    }
    return result;
  }

  static Future<void> clearAll() async {
    final prefs = await SharedPreferences.getInstance();
    final keys = prefs.getKeys().where((k) => k.startsWith(_prefix)).toList();
    for (final key in keys) {
      await prefs.remove(key);
    }
  }
}
