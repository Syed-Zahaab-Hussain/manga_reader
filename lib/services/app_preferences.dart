import 'package:shared_preferences/shared_preferences.dart';

enum ReadingDirection {
  vertical,
  leftToRight,
}

enum HorizontalPageFit {
  width,
  page,
}

class AppPreferences {
  static const mangaFolderPathKey = 'manga_folder_path';
  static const readingDirectionKey = 'reading_direction';
  static const horizontalPageFitKey = 'horizontal_page_fit';

  static Future<String?> getMangaFolderPath() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(mangaFolderPathKey);
  }

  static Future<void> setMangaFolderPath(String path) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(mangaFolderPathKey, path);
  }

  static Future<ReadingDirection> getReadingDirection() async {
    final prefs = await SharedPreferences.getInstance();
    final value = prefs.getString(readingDirectionKey);
    return ReadingDirection.values.firstWhere(
      (direction) => direction.name == value,
      orElse: () => ReadingDirection.vertical,
    );
  }

  static Future<void> setReadingDirection(ReadingDirection direction) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(readingDirectionKey, direction.name);
  }

  static Future<HorizontalPageFit> getHorizontalPageFit() async {
    final prefs = await SharedPreferences.getInstance();
    final value = prefs.getString(horizontalPageFitKey);
    return HorizontalPageFit.values.firstWhere(
      (fit) => fit.name == value,
      orElse: () => HorizontalPageFit.width,
    );
  }

  static Future<void> setHorizontalPageFit(HorizontalPageFit fit) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(horizontalPageFitKey, fit.name);
  }
}
