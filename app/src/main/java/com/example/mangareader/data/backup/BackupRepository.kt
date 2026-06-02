package com.example.mangareader.data.backup

import com.example.mangareader.data.preferences.PreferencesRepository
import com.example.mangareader.data.progress.ProgressRepository
import com.example.mangareader.data.progress.toJson
import com.example.mangareader.data.progress.toMangaProgress
import com.example.mangareader.domain.model.MangaProgress
import com.example.mangareader.domain.model.PageFit
import com.example.mangareader.domain.model.ReadingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ImportResult {
    data class Success(val importedEntries: Int, val mergedEntries: Int) : ImportResult()
    data class Rejected(val reason: RejectionReason) : ImportResult()
}

enum class RejectionReason { INVALID_FORMAT, WRONG_APP, NEWER_SCHEMA }

class BackupRepository(
    private val preferencesRepository: PreferencesRepository,
    private val progressRepository: ProgressRepository
) {

    suspend fun export(output: OutputStream, mangaRoot: File?) = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.snapshot()
        val progressArray = JSONArray()
        progressRepository.load(mangaRoot).values.forEach { progressArray.put(it.toJson()) }
        val doc = JSONObject()
            .put(KEY_APP, APP_ID)
            .put(KEY_BACKUP_SCHEMA_VERSION, BACKUP_SCHEMA_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "preferences",
                JSONObject()
                    .put("readingMode", prefs.readingMode.name)
                    .put("horizontalPageFit", prefs.horizontalPageFit.name)
                    .put("imageWidth", prefs.imageWidthFraction.toDouble())
                    .put("controlsLocked", prefs.controlsLocked)
            )
            .put("progress", progressArray)
        output.use { it.write(doc.toString(2).toByteArray(Charsets.UTF_8)) }
    }

    suspend fun import(input: InputStream, mangaRoot: File?): ImportResult = withContext(Dispatchers.IO) {
        val text = runCatching { input.use { it.readBytes().toString(Charsets.UTF_8) } }
            .getOrNull()
            ?: return@withContext ImportResult.Rejected(RejectionReason.INVALID_FORMAT)
        val doc = runCatching { JSONObject(text) }
            .getOrElse { return@withContext ImportResult.Rejected(RejectionReason.INVALID_FORMAT) }

        if (doc.optString(KEY_APP) != APP_ID) {
            return@withContext ImportResult.Rejected(RejectionReason.WRONG_APP)
        }
        if (!doc.has(KEY_BACKUP_SCHEMA_VERSION)) {
            return@withContext ImportResult.Rejected(RejectionReason.INVALID_FORMAT)
        }
        val schema = doc.optInt(KEY_BACKUP_SCHEMA_VERSION, -1)
        if (schema > BACKUP_SCHEMA_VERSION) {
            return@withContext ImportResult.Rejected(RejectionReason.NEWER_SCHEMA)
        }

        doc.optJSONObject("preferences")?.let { applyPreferences(it) }

        var imported = 0
        var merged = 0
        val array = doc.optJSONArray("progress") ?: JSONArray()
        val existing = progressRepository.load(mangaRoot).toMutableMap()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i)?.toMangaProgress() ?: continue
            imported++
            val current = existing[entry.mangaId]
            if (current != null) merged++
            existing[entry.mangaId] = merge(current, entry)
        }
        if (mangaRoot != null && mangaRoot.isDirectory) {
            progressRepository.save(mangaRoot, existing)
        }
        ImportResult.Success(importedEntries = imported, mergedEntries = merged)
    }

    fun merge(current: MangaProgress?, incoming: MangaProgress): MangaProgress {
        current ?: return incoming
        val completed = current.completedChapters + incoming.completedChapters
        return if (incoming.lastReadTimestamp >= current.lastReadTimestamp) {
            incoming.copy(completedChapters = completed)
        } else {
            current.copy(completedChapters = completed)
        }
    }

    private suspend fun applyPreferences(prefs: JSONObject) {
        runCatching { ReadingMode.valueOf(prefs.optString("readingMode")) }
            .getOrNull()?.let { preferencesRepository.setReadingMode(it) }
        runCatching { PageFit.valueOf(prefs.optString("horizontalPageFit")) }
            .getOrNull()?.let { preferencesRepository.setHorizontalPageFit(it) }
        val width = prefs.optDouble("imageWidth", Double.NaN)
        if (!width.isNaN()) {
            preferencesRepository.setImageWidth(width.toFloat())
        }
        if (prefs.has("controlsLocked")) {
            preferencesRepository.setControlsLocked(prefs.optBoolean("controlsLocked"))
        }
    }

    companion object {
        const val BACKUP_SCHEMA_VERSION = 1
        const val BACKUP_FILE_PREFIX = "manga_reader_backup"
        private const val APP_ID = ProgressRepository.APP_ID
        private const val KEY_APP = "app"
        private const val KEY_BACKUP_SCHEMA_VERSION = "backupSchemaVersion"

        fun defaultFileName(timestampMs: Long = System.currentTimeMillis()): String {
            val format = SimpleDateFormat("yyyyMMdd", Locale.US)
            return "${BACKUP_FILE_PREFIX}_${format.format(Date(timestampMs))}.json"
        }
    }
}
