package com.example.mangareader.data.progress

import com.example.mangareader.domain.model.MangaProgress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class ProgressSerializationTest {

    @Test
    fun `progress JSON round trip preserves all reading state`() {
        val original = MangaProgress(
            mangaId = "Naruto",
            mangaTitle = "Naruto",
            coverPath = "Naruto/cover.jpg",
            coverArchivePath = null,
            coverEntryName = null,
            chapterIndex = 42,
            pageIndex = 29,
            totalChapters = 80,
            completedChapters = setOf(1, 2, 41),
            lastReadTimestamp = 500L,
            chapterPageIndices = mapOf(41 to 10, 42 to 29),
            recentlyReadDismissedAt = 400L
        )

        val restored = original.toJson().toMangaProgress()

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun `legacy progress adds active chapter to per chapter positions`() {
        val legacy = JSONObject()
            .put("mangaId", "Naruto")
            .put("mangaTitle", "Naruto")
            .put("chapterIndex", 4)
            .put("pageIndex", 12)
            .put("totalChapters", 10)
            .put("completedChapters", JSONArray().put(3))
            .put("lastRead", 100L)

        val restored = requireNotNull(legacy.toMangaProgress())

        assertEquals(12, restored.chapterPageIndices[4])
        assertEquals(0L, restored.recentlyReadDismissedAt)
    }

    @Test
    fun `serialization stores a current cover as a relative reference`() {
        val root = File(System.getProperty("java.io.tmpdir"), "manga-root")
        val progress = MangaProgress(
            mangaId = "Naruto",
            mangaTitle = "Naruto",
            coverPath = File(root, "Naruto/cover.jpg").absolutePath,
            coverArchivePath = null,
            coverEntryName = null,
            chapterIndex = 0,
            pageIndex = 0,
            totalChapters = 1,
            completedChapters = emptySet(),
            lastReadTimestamp = 100L
        )

        val json = progress.toJson(root)

        assertEquals("Naruto/cover.jpg", json.getString("coverPath"))
    }
}
