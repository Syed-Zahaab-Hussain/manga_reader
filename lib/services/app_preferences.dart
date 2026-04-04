import 'package:shared_preferences/shared_preferences.dart';

class AppPreferences {
  static const mangaFolderPathKey = 'manga_folder_path';

  static Future<String?> getMangaFolderPath() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(mangaFolderPathKey);
  }

  static Future<void> setMangaFolderPath(String path) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(mangaFolderPathKey, path);
  }
}
