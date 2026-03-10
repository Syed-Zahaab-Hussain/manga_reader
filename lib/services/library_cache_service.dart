import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/manga_item.dart';

class LibraryCacheService {
  static const _key = 'manga_library_cache';

  static Future<void> save(List<MangaItem> items) async {
    final prefs = await SharedPreferences.getInstance();
    final json = jsonEncode(items.map((e) => e.toMap()).toList());
    await prefs.setString(_key, json);
  }

  static Future<List<MangaItem>> load() async {
    final prefs = await SharedPreferences.getInstance();
    final json = prefs.getString(_key);
    if (json == null) return [];
    final list = jsonDecode(json) as List<dynamic>;
    return list.map((e) => MangaItem.fromMap(e as Map<String, dynamic>)).toList();
  }

  static Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }
}
