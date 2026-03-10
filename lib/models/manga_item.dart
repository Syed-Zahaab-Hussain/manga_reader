import 'chapter_item.dart';
import 'reading_progress.dart';

class MangaItem {
  final String id;
  final String title;
  final String path;
  final bool isArchive;
  final List<ChapterItem> chapters;
  final String? coverImagePath;
  final bool coverIsInArchive;
  final String? coverArchiveEntry;
  ReadingProgress? progress;

  int get chapterCount => chapters.length;

  MangaItem({
    required this.id,
    required this.title,
    required this.path,
    required this.isArchive,
    required this.chapters,
    this.coverImagePath,
    required this.coverIsInArchive,
    this.coverArchiveEntry,
    this.progress,
  });

  Map<String, dynamic> toMap() => {
        'id': id,
        'title': title,
        'path': path,
        'isArchive': isArchive,
        'chapters': chapters.map((c) => c.toMap()).toList(),
        'coverImagePath': coverImagePath,
        'coverIsInArchive': coverIsInArchive,
        'coverArchiveEntry': coverArchiveEntry,
      };

  factory MangaItem.fromMap(Map<String, dynamic> map) => MangaItem(
        id: map['id'] as String,
        title: map['title'] as String,
        path: map['path'] as String,
        isArchive: map['isArchive'] as bool,
        chapters: (map['chapters'] as List<dynamic>)
            .map((c) => ChapterItem.fromMap(c as Map<String, dynamic>))
            .toList(),
        coverImagePath: map['coverImagePath'] as String?,
        coverIsInArchive: map['coverIsInArchive'] as bool? ?? false,
        coverArchiveEntry: map['coverArchiveEntry'] as String?,
      );
}
