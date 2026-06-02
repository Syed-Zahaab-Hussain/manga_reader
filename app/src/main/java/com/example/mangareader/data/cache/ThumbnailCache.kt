package com.example.mangareader.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.mangareader.core.io.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File

class ThumbnailCache(context: Context) {

    private val appContext = context.applicationContext

    private val cacheDir: File
        get() = File(appContext.cacheDir, DIR_NAME).apply { mkdirs() }

    suspend fun get(sourceKey: String, sourceMtimeMs: Long?): File? = withContext(Dispatchers.IO) {
        val file = fileFor(sourceKey, sourceMtimeMs)
        if (file.isFile && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            file
        } else {
            null
        }
    }

    suspend fun obtainFromFile(sourcePath: String, mtimeMs: Long?): File? = withContext(Dispatchers.IO) {
        val key = "file::$sourcePath"
        get(key, mtimeMs)?.let { return@withContext it }
        if (!File(sourcePath).isFile) return@withContext null
        val bitmap = decodeScaled(sourcePath) ?: return@withContext null
        writeThumbnail(bitmap, key, mtimeMs)
    }

    suspend fun obtainFromArchiveEntry(
        archivePath: String,
        entryName: String,
        archiveMtimeMs: Long?
    ): File? = withContext(Dispatchers.IO) {
        val key = "archive::$archivePath::$entryName"
        get(key, archiveMtimeMs)?.let { return@withContext it }
        runCatching {
            ZipFile(archivePath).use { zip ->
                val header = zip.getFileHeader(entryName) ?: return@withContext null
                val bytes = zip.getInputStream(header).use { it.readBytes() }
                val bitmap = decodeScaled(bytes) ?: return@withContext null
                writeThumbnail(bitmap, key, archiveMtimeMs)
            }
        }.getOrNull()
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        Unit
    }

    private suspend fun writeThumbnail(bitmap: Bitmap, key: String, mtimeMs: Long?): File =
        withContext(Dispatchers.IO) {
            val target = fileFor(key, mtimeMs)
            val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            enforceLimit()
            target
        }

    private suspend fun enforceLimit() = withContext(Dispatchers.IO) {
        val files = cacheDir.listFiles { f -> f.isFile } ?: return@withContext
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_BYTES) return@withContext
        files.sortedBy { it.lastModified() }.forEach { oldest ->
            if (totalSize <= MAX_CACHE_BYTES) return@forEach
            val size = oldest.length()
            if (oldest.delete()) totalSize -= size
        }
    }

    private fun fileFor(key: String, mtimeMs: Long?): File =
        File(cacheDir, Hashing.sha256("$key|${mtimeMs ?: 0L}") + EXTENSION)

    private fun decodeScaled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        })
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        )
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= TARGET_SIZE && height / (sample * 2) >= TARGET_SIZE) {
            sample *= 2
        }
        return sample
    }

    companion object {
        const val MAX_CACHE_BYTES = 500L * 1024 * 1024
        private const val DIR_NAME = "thumbnails"
        private const val TARGET_SIZE = 512
        private const val JPEG_QUALITY = 85
        private const val EXTENSION = ".jpg"
        private const val TMP_SUFFIX = ".tmp"
    }
}
