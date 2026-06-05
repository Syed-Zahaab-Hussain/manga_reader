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
    val lastReadTimestamp: Long,
    val chapterPageIndices: Map<Int, Int> = emptyMap(),
    val recentlyReadDismissedAt: Long = 0L
)

fun MangaProgress.pageIndexForChapter(chapterIndex: Int): Int? =
    chapterPageIndices[chapterIndex]
        ?: pageIndex.takeIf { this.chapterIndex == chapterIndex }

enum class ReadingMode { VERTICAL, HORIZONTAL }

enum class PageFit { WIDTH, PAGE }

data class ReaderPreferences(
    val mangaFolderPath: String?,
    val readingMode: ReadingMode,
    val horizontalPageFit: PageFit,
    val imageWidthFraction: Float,
    val showPageNumbers: Boolean,
    val zoomEnabled: Boolean,
    val controlsLocked: Boolean
)
