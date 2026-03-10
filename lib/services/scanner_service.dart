import 'dart:async';
import 'dart:io';
import 'dart:isolate';

import 'package:archive/archive_io.dart';
import 'package:path/path.dart' as p;

import '../models/chapter_item.dart';
import '../models/manga_item.dart';

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

sealed class ScannerEvent {}

class ScanProgressEvent extends ScannerEvent {
  final int scanned;
  final int total;
  ScanProgressEvent(this.scanned, this.total);
}

class ScanMangaEvent extends ScannerEvent {
  final MangaItem manga;
  ScanMangaEvent(this.manga);
}

class ScanDoneEvent extends ScannerEvent {}

class ScanErrorEvent extends ScannerEvent {
  final String message;
  ScanErrorEvent(this.message);
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

class ScannerService {
  Isolate? _isolate;

  Stream<ScannerEvent> scan(String rootPath) {
    final controller = StreamController<ScannerEvent>();

    _startIsolate(rootPath, controller);

    return controller.stream;
  }

  Future<void> _startIsolate(
    String rootPath,
    StreamController<ScannerEvent> controller,
  ) async {
    final receivePort = ReceivePort();

    try {
      _isolate = await Isolate.spawn(
        _isolateEntry,
        [receivePort.sendPort, rootPath],
        onError: receivePort.sendPort,
        onExit: receivePort.sendPort,
      );
    } catch (e) {
      controller.add(ScanErrorEvent('Failed to start isolate: $e'));
      await controller.close();
      return;
    }

    receivePort.listen(
      (message) {
        if (controller.isClosed) return;

        if (message == null) {
          // isolate exited normally
          if (!controller.isClosed) controller.close();
          receivePort.close();
          return;
        }

        if (message is List && message.length == 2 && message[0] is String) {
          // onError sends [errorString, stackTraceString]
          controller.add(ScanErrorEvent(message[0] as String));
          controller.close();
          receivePort.close();
          return;
        }

        if (message is! Map<String, dynamic>) return;

        final type = message['type'] as String?;
        switch (type) {
          case 'progress':
            controller.add(ScanProgressEvent(
              message['scanned'] as int,
              message['total'] as int,
            ));
          case 'manga':
            final mangaMap = message['manga'] as Map<String, dynamic>;
            controller.add(ScanMangaEvent(MangaItem.fromMap(mangaMap)));
          case 'done':
            controller.add(ScanDoneEvent());
            controller.close();
            receivePort.close();
          case 'error':
            controller.add(ScanErrorEvent(message['message'] as String));
            controller.close();
            receivePort.close();
        }
      },
      onDone: () {
        if (!controller.isClosed) controller.close();
      },
    );
  }

  void cancel() {
    _isolate?.kill(priority: Isolate.immediate);
    _isolate = null;
  }
}

// ---------------------------------------------------------------------------
// Natural sort (top-level, pure Dart)
// ---------------------------------------------------------------------------

int _naturalCompare(String a, String b) {
  final reg = RegExp(r'(\d+)|(\D+)');
  final aMatches = reg.allMatches(a).toList();
  final bMatches = reg.allMatches(b).toList();

  final len = aMatches.length < bMatches.length ? aMatches.length : bMatches.length;
  for (var i = 0; i < len; i++) {
    final aSeg = aMatches[i].group(0)!;
    final bSeg = bMatches[i].group(0)!;
    final aNum = int.tryParse(aSeg);
    final bNum = int.tryParse(bSeg);
    if (aNum != null && bNum != null) {
      final cmp = aNum.compareTo(bNum);
      if (cmp != 0) return cmp;
    } else {
      final cmp = aSeg.compareTo(bSeg);
      if (cmp != 0) return cmp;
    }
  }
  return aMatches.length.compareTo(bMatches.length);
}

// ---------------------------------------------------------------------------
// Image extension check (top-level)
// ---------------------------------------------------------------------------

bool _isImageFile(String name) {
  final lower = name.toLowerCase();
  return lower.endsWith('.jpg') ||
      lower.endsWith('.jpeg') ||
      lower.endsWith('.png') ||
      lower.endsWith('.webp');
}

bool _isCoverFile(String name) {
  final lower = p.basenameWithoutExtension(name).toLowerCase();
  return lower == 'cover' && _isImageFile(name);
}

// ---------------------------------------------------------------------------
// Isolate entry (top-level — required for Isolate.spawn)
// ---------------------------------------------------------------------------

void _isolateEntry(List<dynamic> args) {
  final sendPort = args[0] as SendPort;
  final rootPath = args[1] as String;

  try {
    final rootDir = Directory(rootPath);
    if (!rootDir.existsSync()) {
      sendPort.send({
        'type': 'error',
        'message': 'Directory does not exist: $rootPath',
      });
      return;
    }

    // Gather candidates: immediate subdirs + .zip/.cbz files
    final List<FileSystemEntity> entries;
    try {
      entries = rootDir.listSync(followLinks: false);
    } catch (e) {
      sendPort.send({'type': 'error', 'message': 'Cannot list directory: $e'});
      return;
    }

    final candidates = <FileSystemEntity>[];
    for (final entry in entries) {
      if (entry is Directory) {
        candidates.add(entry);
      } else if (entry is File) {
        final lower = entry.path.toLowerCase();
        if (lower.endsWith('.zip') || lower.endsWith('.cbz')) {
          candidates.add(entry);
        }
      }
    }

    // Natural sort candidates by basename
    candidates.sort((a, b) =>
        _naturalCompare(p.basename(a.path), p.basename(b.path)));

    final total = candidates.length;
    sendPort.send({'type': 'progress', 'scanned': 0, 'total': total});

    var scanned = 0;
    for (final candidate in candidates) {
      MangaItem? manga;
      if (candidate is Directory) {
        manga = _scanFolderManga(candidate);
      } else if (candidate is File) {
        manga = _scanArchiveManga(candidate);
      }

      scanned++;
      if (manga != null) {
        sendPort.send({'type': 'manga', 'manga': manga.toMap()});
      }
      sendPort.send({'type': 'progress', 'scanned': scanned, 'total': total});
    }

    sendPort.send({'type': 'done'});
  } catch (e, st) {
    sendPort.send({'type': 'error', 'message': 'Scanner error: $e\n$st'});
  }
}

// ---------------------------------------------------------------------------
// Folder manga scanner
// ---------------------------------------------------------------------------

MangaItem? _scanFolderManga(Directory dir) {
  final mangaPath = dir.path;
  final title = p.basename(mangaPath);

  List<FileSystemEntity> contents;
  try {
    contents = dir.listSync(followLinks: false);
  } catch (_) {
    return null;
  }

  // Natural-sort contents by basename
  contents.sort((a, b) => _naturalCompare(p.basename(a.path), p.basename(b.path)));

  final subDirs = contents.whereType<Directory>().toList();
  final rootImages =
      contents.whereType<File>().where((f) => _isImageFile(f.path)).toList();

  final chapters = <ChapterItem>[];
  String? coverImagePath;

  // Check for cover.{ext} in manga root
  final coverFile = rootImages.where((f) => _isCoverFile(p.basename(f.path))).firstOrNull;
  if (coverFile != null) {
    coverImagePath = coverFile.path;
  }

  if (subDirs.isNotEmpty) {
    // Each subfolder with images is a chapter; loose root images are ignored (per spec)
    for (final subDir in subDirs) {
      List<FileSystemEntity> subContents;
      try {
        subContents = subDir.listSync(followLinks: false);
      } catch (_) {
        continue;
      }
      subContents.sort(
          (a, b) => _naturalCompare(p.basename(a.path), p.basename(b.path)));

      final subImages = subContents
          .whereType<File>()
          .where((f) => _isImageFile(f.path))
          .toList();
      if (subImages.isEmpty) continue;

      chapters.add(ChapterItem(
        title: p.basename(subDir.path),
        path: subDir.path,
        isArchive: false,
        pageCount: subImages.length,
      ));

      // Use first image of first chapter as cover if no explicit cover found
      if (coverImagePath == null && chapters.length == 1) {
        coverImagePath = subImages.first.path;
      }
    }
  } else {
    // No subfolders — images directly in folder = single chapter
    if (rootImages.isEmpty) return null;

    final nonCoverImages =
        rootImages.where((f) => !_isCoverFile(p.basename(f.path))).toList();
    final chapterImages = nonCoverImages.isNotEmpty ? nonCoverImages : rootImages;

    chapters.add(ChapterItem(
      title: title,
      path: mangaPath,
      isArchive: false,
      pageCount: chapterImages.length,
    ));

    coverImagePath ??= chapterImages.first.path;
  }

  if (chapters.isEmpty) return null;

  return MangaItem(
    id: mangaPath,
    title: title,
    path: mangaPath,
    isArchive: false,
    chapters: chapters,
    coverImagePath: coverImagePath,
    coverIsInArchive: false,
  );
}

// ---------------------------------------------------------------------------
// Archive manga scanner
// ---------------------------------------------------------------------------

MangaItem? _scanArchiveManga(File file) {
  final archivePath = file.path;
  final title = p.basenameWithoutExtension(archivePath);

  Archive archive;
  InputFileStream? inputStream;
  try {
    inputStream = InputFileStream(file.path);
    archive = ZipDecoder().decodeStream(inputStream);
  } catch (_) {
    inputStream?.closeSync();
    return null; // corrupted — skip
  } finally {
    inputStream?.closeSync();
  }

  // Filter image entries
  final imageEntries = archive.files
      .where((e) => e.isFile && _isImageFile(e.name))
      .toList();

  if (imageEntries.isEmpty) return null;

  // Natural sort by entry name
  imageEntries
      .sort((a, b) => _naturalCompare(a.name, b.name));

  // Detect top-level subdirectories
  final subDirNames = <String>{};
  for (final entry in imageEntries) {
    final parts = entry.name.split('/');
    if (parts.length > 1) {
      subDirNames.add(parts[0]);
    }
  }

  final chapters = <ChapterItem>[];
  String? coverImagePath;
  bool coverIsInArchive = false;
  String? coverArchiveEntry;

  // Look for cover.{ext} at archive root (no '/' in name)
  final rootCover = imageEntries
      .where((e) => !e.name.contains('/') && _isCoverFile(e.name))
      .firstOrNull;
  if (rootCover != null) {
    coverImagePath = archivePath;
    coverIsInArchive = true;
    coverArchiveEntry = rootCover.name;
  }

  if (subDirNames.isNotEmpty) {
    // Sort subdirectory names naturally
    final sortedDirNames = subDirNames.toList()
      ..sort(_naturalCompare);

    for (final dirName in sortedDirNames) {
      final prefix = '$dirName/';
      final dirImages =
          imageEntries.where((e) => e.name.startsWith(prefix)).toList();
      if (dirImages.isEmpty) continue;

      chapters.add(ChapterItem(
        title: dirName,
        path: archivePath,
        isArchive: true,
        archiveEntryPrefix: prefix,
        pageCount: dirImages.length,
      ));

      // First image of first chapter as cover fallback
      if (coverArchiveEntry == null && chapters.length == 1) {
        coverImagePath = archivePath;
        coverIsInArchive = true;
        coverArchiveEntry = dirImages.first.name;
      }
    }
  } else {
    // Flat archive — single chapter, all images
    chapters.add(ChapterItem(
      title: title,
      path: archivePath,
      isArchive: true,
      archiveEntryPrefix: '',
      pageCount: imageEntries.length,
    ));

    if (coverArchiveEntry == null) {
      coverImagePath = archivePath;
      coverIsInArchive = true;
      coverArchiveEntry = imageEntries.first.name;
    }
  }

  if (chapters.isEmpty) return null;

  return MangaItem(
    id: archivePath,
    title: title,
    path: archivePath,
    isArchive: true,
    chapters: chapters,
    coverImagePath: coverImagePath,
    coverIsInArchive: coverIsInArchive,
    coverArchiveEntry: coverArchiveEntry,
  );
}
