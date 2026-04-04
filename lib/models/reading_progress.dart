import 'dart:convert';

class ReadingProgress {
  final String mangaId;
  final String mangaTitle;
  final String? coverImagePath;
  final bool coverIsInArchive;
  final String? coverArchiveEntry;
  final int chapterIndex;
  final int pageIndex;
  final int totalChapters;
  final DateTime lastRead;

  double get overallProgress =>
      totalChapters > 0 ? chapterIndex / totalChapters : 0.0;

  ReadingProgress({
    required this.mangaId,
    required this.mangaTitle,
    this.coverImagePath,
    required this.coverIsInArchive,
    this.coverArchiveEntry,
    required this.chapterIndex,
    required this.pageIndex,
    required this.totalChapters,
    required this.lastRead,
  });

  ReadingProgress copyWith({
    String? mangaId,
    String? mangaTitle,
    String? coverImagePath,
    bool? coverIsInArchive,
    String? coverArchiveEntry,
    int? chapterIndex,
    int? pageIndex,
    int? totalChapters,
    DateTime? lastRead,
  }) =>
      ReadingProgress(
        mangaId: mangaId ?? this.mangaId,
        mangaTitle: mangaTitle ?? this.mangaTitle,
        coverImagePath: coverImagePath ?? this.coverImagePath,
        coverIsInArchive: coverIsInArchive ?? this.coverIsInArchive,
        coverArchiveEntry: coverArchiveEntry ?? this.coverArchiveEntry,
        chapterIndex: chapterIndex ?? this.chapterIndex,
        pageIndex: pageIndex ?? this.pageIndex,
        totalChapters: totalChapters ?? this.totalChapters,
        lastRead: lastRead ?? this.lastRead,
      );

  Map<String, dynamic> toJson() => {
        'mangaId': mangaId,
        'mangaTitle': mangaTitle,
        'coverImagePath': coverImagePath,
        'coverIsInArchive': coverIsInArchive,
        'coverArchiveEntry': coverArchiveEntry,
        'chapterIndex': chapterIndex,
        'pageIndex': pageIndex,
        'totalChapters': totalChapters,
        'lastRead': lastRead.toIso8601String(),
      };

  factory ReadingProgress.fromJson(Map<String, dynamic> json) =>
      ReadingProgress(
        mangaId: json['mangaId'] as String,
        mangaTitle: json['mangaTitle'] as String,
        coverImagePath: json['coverImagePath'] as String?,
        coverIsInArchive: json['coverIsInArchive'] as bool? ?? false,
        coverArchiveEntry: json['coverArchiveEntry'] as String?,
        chapterIndex: json['chapterIndex'] as int,
        pageIndex: json['pageIndex'] as int,
        totalChapters: json['totalChapters'] as int,
        lastRead: DateTime.parse(json['lastRead'] as String),
      );

  String toJsonString() => jsonEncode(toJson());

  factory ReadingProgress.fromJsonString(String s) =>
      ReadingProgress.fromJson(jsonDecode(s) as Map<String, dynamic>);
}
