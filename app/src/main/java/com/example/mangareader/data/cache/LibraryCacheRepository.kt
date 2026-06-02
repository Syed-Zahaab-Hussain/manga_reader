package com.example.mangareader.data.cache

import android.content.Context
import com.example.mangareader.domain.model.ChapterItem
import com.example.mangareader.domain.model.MangaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LibraryCacheRepository(private val context: Context) {

    data class CachedLibrary(val folderPath: String?, val items: List<MangaItem>)

    private val cacheFile: File
        get() = File(context.applicationContext.filesDir, FILE_NAME)

    suspend fun load(): CachedLibrary? = withContext(Dispatchers.IO) {
        if (!cacheFile.isFile) return@withContext null
        runCatching {
            val doc = JSONObject(cacheFile.readText())
            val array = doc.optJSONArray(KEY_ITEMS) ?: return@runCatching null
            val items = ArrayList<MangaItem>(array.length())
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.toMangaItem()?.let(items::add)
            }
            CachedLibrary(
                folderPath = doc.optString(KEY_FOLDER_PATH).ifEmpty { null },
                items = items
            )
        }.getOrNull()
    }

    suspend fun save(folderPath: String?, items: List<MangaItem>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        val doc = JSONObject()
            .put(KEY_FOLDER_PATH, folderPath ?: "")
            .put(KEY_ITEMS, array)
        val tmp = File(cacheFile.parentFile, cacheFile.name + TMP_SUFFIX)
        tmp.writeText(doc.toString(2))
        if (cacheFile.exists()) cacheFile.delete()
        if (!tmp.renameTo(cacheFile)) {
            tmp.copyTo(cacheFile, overwrite = true)
            tmp.delete()
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cacheFile.delete()
        Unit
    }

    companion object {
        private const val FILE_NAME = "library_cache.json"
        private const val KEY_FOLDER_PATH = "folderPath"
        private const val KEY_ITEMS = "items"
        private const val TMP_SUFFIX = ".tmp"
    }
}

internal fun MangaItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("rootPath", rootPath)
    .put("isArchive", isArchive)
    .put("coverPath", coverPath)
    .put("coverArchive", coverArchivePath)
    .put("coverEntry", coverEntryName)
    .put(
        "chapters",
        JSONArray().apply {
            chapters.forEach { chapter ->
                put(
                    JSONObject()
                        .put("index", chapter.index)
                        .put("title", chapter.title)
                        .put("path", chapter.path)
                        .put("pageCount", chapter.pageCount)
                        .put("isArchiveChapter", chapter.isArchiveChapter)
                        .put("archivePath", chapter.archivePath)
                        .put("archivePrefix", chapter.archivePrefix)
                )
            }
        }
    )

internal fun JSONObject.toMangaItem(): MangaItem? {
    val id = optString("id", "").ifEmpty { null } ?: return null
    val chapters = ArrayList<ChapterItem>()
    optJSONArray("chapters")?.let { array ->
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            chapters.add(
                ChapterItem(
                    index = obj.optInt("index", i),
                    title = obj.optString("title", ""),
                    path = obj.optString("path", ""),
                    pageCount = obj.optInt("pageCount", 0),
                    isArchiveChapter = obj.optBoolean("isArchiveChapter", false),
                    archivePath = obj.optString("archivePath", "").ifEmpty { null },
                    archivePrefix = obj.optString("archivePrefix", "").ifEmpty { null }
                )
            )
        }
    }
    return MangaItem(
        id = id,
        title = optString("title", ""),
        rootPath = optString("rootPath", ""),
        isArchive = optBoolean("isArchive", false),
        coverPath = optString("coverPath", "").ifEmpty { null },
        coverArchivePath = optString("coverArchive", "").ifEmpty { null },
        coverEntryName = optString("coverEntry", "").ifEmpty { null },
        chapters = chapters
    )
}
