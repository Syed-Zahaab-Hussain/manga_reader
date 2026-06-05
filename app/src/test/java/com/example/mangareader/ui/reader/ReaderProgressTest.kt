package com.example.mangareader.ui.reader

import com.example.mangareader.domain.model.ChapterItem
import com.example.mangareader.domain.model.MangaItem
import com.example.mangareader.domain.model.MangaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressTest {

    @Test
    fun visiblePageUpdatePreservesPreviouslyCompletedChapters() {
        val result = buildReaderProgress(
            manga = manga,
            chapter = manga.chapters[1],
            pageIndex = 4,
            prior = progress(completed = setOf(0)),
            markCompleted = false,
            timestamp = 123L
        )

        assertEquals(1, result.chapterIndex)
        assertEquals(4, result.pageIndex)
        assertEquals(setOf(0), result.completedChapters)
        assertEquals(123L, result.lastReadTimestamp)
    }

    @Test
    fun reachingLastPageAddsCurrentChapterToCompletedSet() {
        val result = buildReaderProgress(
            manga = manga,
            chapter = manga.chapters[1],
            pageIndex = 9,
            prior = progress(completed = setOf(0)),
            markCompleted = true,
            timestamp = 456L
        )

        assertEquals(setOf(0, 1), result.completedChapters)
        assertTrue(1 in result.completedChapters)
    }

    private fun progress(completed: Set<Int>) = MangaProgress(
        mangaId = manga.id,
        mangaTitle = manga.title,
        coverPath = null,
        coverArchivePath = null,
        coverEntryName = null,
        chapterIndex = 0,
        pageIndex = 2,
        totalChapters = manga.chapters.size,
        completedChapters = completed,
        lastReadTimestamp = 1L
    )

    private companion object {
        val manga = MangaItem(
            id = "manga-id",
            title = "Test Manga",
            rootPath = "/manga",
            isArchive = false,
            coverPath = null,
            coverArchivePath = null,
            coverEntryName = null,
            chapters = listOf(chapter(0), chapter(1))
        )

        fun chapter(index: Int) = ChapterItem(
            index = index,
            title = "Chapter ${index + 1}",
            path = "/manga/chapter-${index + 1}",
            pageCount = 10,
            isArchiveChapter = false,
            archivePath = null,
            archivePrefix = null
        )
    }
}
