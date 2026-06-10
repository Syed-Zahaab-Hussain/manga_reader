package com.example.mangareader.data.backup

import com.example.mangareader.domain.model.MangaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackupMergeTest {

    @Test
    fun `missing current progress returns incoming unchanged`() {
        val incoming = progress(timestamp = 20L, chapter = 2, page = 8)

        assertSame(incoming, mergeProgress(null, incoming))
    }

    @Test
    fun `newer incoming progress supplies active fields and conflicting page`() {
        val current = progress(timestamp = 10L, chapter = 1, page = 3)
            .copy(chapterPageIndices = mapOf(1 to 3, 4 to 10))
        val incoming = progress(timestamp = 20L, chapter = 2, page = 8)
            .copy(chapterPageIndices = mapOf(2 to 8, 4 to 15))

        val merged = mergeProgress(current, incoming)

        assertEquals(2, merged.chapterIndex)
        assertEquals(8, merged.pageIndex)
        assertEquals(15, merged.chapterPageIndices[4])
    }

    @Test
    fun `completed chapters are always combined`() {
        val current = progress(timestamp = 20L, chapter = 1, page = 3)
            .copy(completedChapters = setOf(1))
        val incoming = progress(timestamp = 10L, chapter = 2, page = 8)
            .copy(completedChapters = setOf(2))

        assertEquals(setOf(1, 2), mergeProgress(current, incoming).completedChapters)
    }

    @Test
    fun `older incoming progress cannot replace newer conflicting page`() {
        val current = progress(timestamp = 20L, chapter = 1, page = 3)
            .copy(chapterPageIndices = mapOf(4 to 15))
        val incoming = progress(timestamp = 10L, chapter = 2, page = 8)
            .copy(chapterPageIndices = mapOf(4 to 10))

        assertEquals(15, mergeProgress(current, incoming).chapterPageIndices[4])
    }

    @Test
    fun `equal timestamps deterministically favor incoming`() {
        val current = progress(timestamp = 20L, chapter = 1, page = 3)
        val incoming = progress(timestamp = 20L, chapter = 2, page = 8)

        val merged = mergeProgress(current, incoming)

        assertEquals(2, merged.chapterIndex)
        assertEquals(8, merged.pageIndex)
    }

    private fun progress(timestamp: Long, chapter: Int, page: Int) = MangaProgress(
        mangaId = "manga",
        mangaTitle = "Manga",
        coverPath = "Manga/cover.jpg",
        coverArchivePath = null,
        coverEntryName = null,
        chapterIndex = chapter,
        pageIndex = page,
        totalChapters = 10,
        completedChapters = emptySet(),
        lastReadTimestamp = timestamp,
        chapterPageIndices = mapOf(chapter to page)
    )
}
