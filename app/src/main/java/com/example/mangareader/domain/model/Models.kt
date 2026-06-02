package com.example.mangareader.domain.model

data class ChapterItem(
    val index: Int,
    val title: String,
    val path: String,
    val pageCount: Int,
    val isArchiveChapter: Boolean,
    val archivePath: String?,
    val archivePrefix: String?
)

data class MangaItem(
    val id: String,
    val title: String,
    val rootPath: String,
    val isArchive: Boolean,
    val coverPath: String?,
    val coverArchivePath: String?,
    val coverEntryName: String?,
    val chapters: List<ChapterItem>
)

data class ScanWarning(
    val path: String,
    val message: String
)

data class MangaProgress(
    val mangaId: String,
    val mangaTitle: String,
    val coverPath: String?,
    val coverArchivePath: String?,
    val coverEntryName: String?,
    val chapterIndex: Int,
    val pageIndex: Int,
    val totalChapters: Int,
    val completedChapters: Set<Int>,
    val lastReadTimestamp: Long
)

enum class ReadingMode { VERTICAL, HORIZONTAL }

enum class PageFit { WIDTH, PAGE }

data class ReaderPreferences(
    val mangaFolderPath: String?,
    val readingMode: ReadingMode,
    val horizontalPageFit: PageFit,
    val imageWidthFraction: Float,
    val controlsLocked: Boolean
)
