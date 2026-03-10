class ChapterItem {
  final String title;
  final String path;
  final bool isArchive;
  final String? archiveEntryPrefix;
  final int pageCount;

  const ChapterItem({
    required this.title,
    required this.path,
    required this.isArchive,
    this.archiveEntryPrefix,
    required this.pageCount,
  });

  Map<String, dynamic> toMap() => {
        'title': title,
        'path': path,
        'isArchive': isArchive,
        'archiveEntryPrefix': archiveEntryPrefix,
        'pageCount': pageCount,
      };

  factory ChapterItem.fromMap(Map<String, dynamic> map) => ChapterItem(
        title: map['title'] as String,
        path: map['path'] as String,
        isArchive: map['isArchive'] as bool,
        archiveEntryPrefix: map['archiveEntryPrefix'] as String?,
        pageCount: map['pageCount'] as int,
      );
}
