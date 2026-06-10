package com.example.mangareader.data.progress

import com.example.mangareader.core.io.PortablePath
import com.example.mangareader.domain.model.MangaProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProgressRepository {

    suspend fun load(root: File?): Map<String, MangaProgress> = withContext(Dispatchers.IO) {
        val file = progressFile(root) ?: return@withContext emptyMap()
        if (!file.isFile) return@withContext emptyMap()
        runCatching {
            val doc = JSONObject(file.readText())
            val arr = doc.optJSONArray(KEY_PROGRESS)
                ?: return@runCatching emptyMap<String, MangaProgress>()
            val result = LinkedHashMap<String, MangaProgress>()
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i)?.toMangaProgress() ?: continue
                result[entry.mangaId] = entry
            }
            result
        }.getOrDefault(emptyMap())
    }

    suspend fun upsert(root: File, entry: MangaProgress) {
        val all = load(root).toMutableMap()
        all[entry.mangaId] = entry
        save(root, all)
    }

    suspend fun remove(root: File, mangaId: String): Boolean {
        val all = load(root).toMutableMap()
        val removed = all.remove(mangaId) != null
        if (removed) save(root, all)
        return removed
    }

    suspend fun save(root: File, progress: Map<String, MangaProgress>) = withContext(Dispatchers.IO) {
        val target = progressFile(root) ?: return@withContext
        val arr = JSONArray()
        progress.values.sortedBy { it.mangaTitle.lowercase() }.forEach {
            arr.put(it.toJson(root))
        }
        val doc = JSONObject()
            .put(KEY_APP, APP_ID)
            .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .put(KEY_PROGRESS, arr)
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        tmp.writeText(doc.toString(2))
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    suspend fun reset(root: File?) = withContext(Dispatchers.IO) {
        progressFile(root)?.delete()
        Unit
    }

    private fun progressFile(root: File?): File? {
        root ?: return null
        if (!root.isDirectory) return null
        return File(root, FILE_NAME)
    }

    companion object {
        const val FILE_NAME = ".manga_reader_progress.json"
        const val APP_ID = "manga_reader"
        const val SCHEMA_VERSION = 1
        private const val KEY_APP = "app"
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_PROGRESS = "progress"
        private const val TMP_SUFFIX = ".tmp"
    }
}

internal fun MangaProgress.toJson(root: File? = null): JSONObject = JSONObject()
    .put("mangaId", mangaId)
    .put("mangaTitle", mangaTitle)
    .put("coverPath", PortablePath.relativeToRoot(root, coverPath))
    .put("coverArchive", PortablePath.relativeToRoot(root, coverArchivePath))
    .put("coverEntry", coverEntryName)
    .put("chapterIndex", chapterIndex)
    .put("pageIndex", pageIndex)
    .put("totalChapters", totalChapters)
    .put("completedChapters", JSONArray(completedChapters.sorted()))
    .put(
        "chapterPageIndices",
        JSONObject().also { positions ->
            (chapterPageIndices + (chapterIndex to pageIndex))
                .toSortedMap()
                .forEach { (chapter, page) -> positions.put(chapter.toString(), page) }
        }
    )
    .put("recentlyReadDismissedAt", recentlyReadDismissedAt)
    .put("lastRead", lastReadTimestamp)

internal fun JSONObject.toMangaProgress(): MangaProgress? {
    val id = optStringOrNull("mangaId") ?: return null
    val activeChapterIndex = optInt("chapterIndex", 0)
    val activePageIndex = optInt("pageIndex", 0).coerceAtLeast(0)
    val chapterPageIndices = buildMap {
        optJSONObject("chapterPageIndices")?.let { positions ->
            positions.keys().forEach { key ->
                val chapter = key.toIntOrNull() ?: return@forEach
                val page = positions.optInt(key, -1)
                if (chapter >= 0 && page >= 0) put(chapter, page)
            }
        }
        putIfAbsent(activeChapterIndex, activePageIndex)
    }
    return MangaProgress(
        mangaId = id,
        mangaTitle = optString("mangaTitle", ""),
        coverPath = optStringOrNull("coverPath"),
        coverArchivePath = optStringOrNull("coverArchive"),
        coverEntryName = optStringOrNull("coverEntry"),
        chapterIndex = activeChapterIndex,
        pageIndex = activePageIndex,
        totalChapters = optInt("totalChapters", 0),
        completedChapters = optJSONArray("completedChapters")
            ?.let { array ->
                (0 until array.length()).mapNotNull { k -> if (array.isNull(k)) null else array.getInt(k) }.toSet()
            }
            ?: emptySet(),
        lastReadTimestamp = optLong("lastRead", 0L),
        chapterPageIndices = chapterPageIndices,
        recentlyReadDismissedAt = optLong("recentlyReadDismissedAt", 0L)
    )
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    optString(key, "").ifEmpty { null }
