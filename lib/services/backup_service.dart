import 'dart:convert';
import 'dart:typed_data';

import 'app_preferences.dart';
import 'progress_service.dart';

class BackupImportResult {
  final int importedProgressEntries;

  const BackupImportResult({required this.importedProgressEntries});
}

class BackupService {
  static const int schemaVersion = 1;

  static Future<Uint8List> createBackupBytes() async {
    final backup = {
      'schemaVersion': schemaVersion,
      'createdAt': DateTime.now().toIso8601String(),
      'app': 'manga_reader',
      'settings': await AppPreferences.exportSettings(),
      'readingProgress': await ProgressService.exportStoredProgress(),
    };

    const encoder = JsonEncoder.withIndent('  ');
    return Uint8List.fromList(utf8.encode(encoder.convert(backup)));
  }

  static Future<BackupImportResult> importBackupBytes(
    Uint8List bytes, {
    bool importSettings = true,
    bool importProgress = true,
  }) async {
    final decoded = jsonDecode(utf8.decode(bytes));
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('The selected file is not a valid backup.');
    }

    if (decoded['app'] != 'manga_reader') {
      throw const FormatException('This backup was not created by this app.');
    }

    final version = decoded['schemaVersion'];
    if (version is! int || version > schemaVersion) {
      throw const FormatException(
        'This backup uses a newer format. Update the app and try again.',
      );
    }

    if (importSettings) {
      final settings = decoded['settings'];
      if (settings is Map<String, dynamic>) {
        await AppPreferences.importSettings(settings);
      }
    }

    var importedProgressEntries = 0;
    if (importProgress) {
      final progress = decoded['readingProgress'];
      if (progress is List<dynamic>) {
        importedProgressEntries = await ProgressService.importStoredProgress(
          progress,
          merge: true,
        );
      }
    }

    return BackupImportResult(
      importedProgressEntries: importedProgressEntries,
    );
  }
}
