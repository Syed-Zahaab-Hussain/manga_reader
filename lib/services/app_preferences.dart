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
  static const horizontalImageWidthKey = 'horizontal_image_width';
  static const readerControlsLockedKey = 'reader_controls_locked';

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

  static Future<double> getReaderImageWidth() async {
    final prefs = await SharedPreferences.getInstance();
    return (prefs.getDouble(horizontalImageWidthKey) ?? 1.0)
        .clamp(0.4, 1.0)
        .toDouble();
  }

  static Future<void> setReaderImageWidth(double width) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(
      horizontalImageWidthKey,
      width.clamp(0.4, 1.0).toDouble(),
    );
  }

  static Future<bool> getReaderControlsLocked() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(readerControlsLockedKey) ?? false;
  }

  static Future<void> setReaderControlsLocked(bool locked) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(readerControlsLockedKey, locked);
  }

  static Future<Map<String, dynamic>> exportSettings() async {
    final prefs = await SharedPreferences.getInstance();
    return {
      mangaFolderPathKey: prefs.getString(mangaFolderPathKey),
      readingDirectionKey: prefs.getString(readingDirectionKey),
      horizontalPageFitKey: prefs.getString(horizontalPageFitKey),
      horizontalImageWidthKey: prefs.getDouble(horizontalImageWidthKey),
      readerControlsLockedKey: prefs.getBool(readerControlsLockedKey),
    };
  }

  static Future<void> importSettings(Map<String, dynamic> settings) async {
    final prefs = await SharedPreferences.getInstance();

    final readingDirection = settings[readingDirectionKey];
    if (readingDirection is String &&
        ReadingDirection.values.any((item) => item.name == readingDirection)) {
      await prefs.setString(readingDirectionKey, readingDirection);
    }

    final pageFit = settings[horizontalPageFitKey];
    if (pageFit is String &&
        HorizontalPageFit.values.any((item) => item.name == pageFit)) {
      await prefs.setString(horizontalPageFitKey, pageFit);
    }

    final imageWidth = settings[horizontalImageWidthKey];
    if (imageWidth is num) {
      await prefs.setDouble(
        horizontalImageWidthKey,
        imageWidth.toDouble().clamp(0.4, 1.0).toDouble(),
      );
    }

    final controlsLocked = settings[readerControlsLockedKey];
    if (controlsLocked is bool) {
      await prefs.setBool(readerControlsLockedKey, controlsLocked);
    }
  }
}
