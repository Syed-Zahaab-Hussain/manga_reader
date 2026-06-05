package com.example.mangareader.ui.library

import com.example.mangareader.domain.model.MangaProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentlyReadTest {

    @Test
    fun `dismissed progress is hidden without being deleted`() {
        val progress = progress(lastRead = 100L, dismissedAt = 100L)

        assertFalse(isVisibleInRecentlyRead(progress))
        assertTrue(progress.chapterPageIndices.isNotEmpty())
    }

    @Test
    fun `new reading after dismissal becomes visible again`() {
        assertTrue(isVisibleInRecentlyRead(progress(lastRead = 200L, dismissedAt = 100L)))
    }

    private fun progress(lastRead: Long, dismissedAt: Long) = MangaProgress(
        mangaId = "manga-id",
        mangaTitle = "Manga",
        coverPath = null,
        coverArchivePath = null,
        coverEntryName = null,
        chapterIndex = 2,
        pageIndex = 7,
        totalChapters = 10,
        completedChapters = setOf(0, 1),
        lastReadTimestamp = lastRead,
        chapterPageIndices = mapOf(2 to 7),
        recentlyReadDismissedAt = dismissedAt
    )
}
