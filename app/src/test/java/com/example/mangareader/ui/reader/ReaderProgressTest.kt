package com.example.mangareader.ui.reader

import com.example.mangareader.domain.model.ChapterItem
import com.example.mangareader.domain.model.MangaItem
import com.example.mangareader.domain.model.MangaProgress
import com.example.mangareader.domain.model.pageIndexForChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressTest {

    @Test
    fun `next chapter preload starts within final three pages`() {
        assertFalse(shouldPreloadNextChapter(currentPageIndex = 6, lastPageIndex = 9))
        assertTrue(shouldPreloadNextChapter(currentPageIndex = 7, lastPageIndex = 9))
        assertTrue(shouldPreloadNextChapter(currentPageIndex = 9, lastPageIndex = 9))
    }

    @Test
    fun `short chapters preload immediately`() {
        assertTrue(shouldPreloadNextChapter(currentPageIndex = 0, lastPageIndex = 2))
        assertFalse(shouldPreloadNextChapter(currentPageIndex = 0, lastPageIndex = -1))
    }

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
        assertEquals(2, result.chapterPageIndices[0])
        assertEquals(4, result.chapterPageIndices[1])
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

    @Test
    fun `returning to a chapter preserves its last page`() {
        val chapter42Progress = buildReaderProgress(
            manga = manga,
            chapter = manga.chapters[1],
            pageIndex = 8,
            prior = progress(completed = emptySet()),
            markCompleted = false,
            timestamp = 100L
        )
        val chapter41Progress = buildReaderProgress(
            manga = manga,
            chapter = manga.chapters[0],
            pageIndex = 3,
            prior = chapter42Progress,
            markCompleted = false,
            timestamp = 200L
        )

        assertEquals(3, chapter41Progress.pageIndexForChapter(0))
        assertEquals(8, chapter41Progress.pageIndexForChapter(1))
    }

    @Test
    fun `reading again clears recently read dismissal`() {
        val dismissed = progress(completed = emptySet()).copy(
            lastReadTimestamp = 100L,
            recentlyReadDismissedAt = 100L
        )

        val result = buildReaderProgress(
            manga = manga,
            chapter = manga.chapters[0],
            pageIndex = 4,
            prior = dismissed,
            markCompleted = false,
            timestamp = 200L
        )

        assertEquals(0L, result.recentlyReadDismissedAt)
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
