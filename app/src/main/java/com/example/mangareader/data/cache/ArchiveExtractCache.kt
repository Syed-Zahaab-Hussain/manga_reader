package com.example.mangareader.data.cache

import android.content.Context
import com.example.mangareader.core.io.Hashing
import com.example.mangareader.core.io.ImageSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.File
import java.io.IOException

class ArchiveExtractCache(private val context: Context) {

    private val rootDir: File
        get() = File(context.applicationContext.cacheDir, DIR_NAME)

    suspend fun extractChapter(archiveFile: File, prefix: String): List<File> =
        withContext(Dispatchers.IO) {
            val archiveDir = File(rootDir, Hashing.sha256(archiveFile.absolutePath))
            val chapterDir = File(archiveDir, Hashing.sha256(prefix))
            if (chapterDir.isDirectory && chapterDir.listFiles().orEmpty().isNotEmpty()) {
                return@withContext collectedImages(chapterDir)
            }
            chapterDir.deleteRecursively()
            chapterDir.mkdirs()
            try {
                ZipFile(archiveFile).use { zip ->
                    val headers = zip.fileHeaders
                        ?: throw IOException("Unreadable archive: ${archiveFile.name}")
                    val normalizedPrefix = prefix.trim('/').replace('\\', '/')
                    val prefixWithSlash = if (normalizedPrefix.isEmpty()) "" else "$normalizedPrefix/"
                    var index = 0
                    for (header in headers) {
                        if (header.isDirectory) continue
                        val name = header.fileName.replace('\\', '/')
                        val relative = if (prefixWithSlash.isEmpty()) {
                            name
                        } else {
                            if (!name.startsWith(prefixWithSlash)) continue
                            name.removePrefix(prefixWithSlash)
                        }
                        if (relative.isEmpty()) continue
                        if (relative.contains('/') || relative.contains("..")) continue
                        if (!ImageSupport.isImage(relative)) continue
                        val extension = relative.substringAfterLast('.', "jpg").lowercase()
                        val out = File(chapterDir, "%04d.$extension".format(index))
                        zip.getInputStream(header).use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                        index++
                    }
                }
            } catch (e: ZipException) {
                chapterDir.deleteRecursively()
                throw IOException("Corrupted, password-protected, or unsupported archive: ${archiveFile.name}", e)
            } catch (e: IOException) {
                chapterDir.deleteRecursively()
                throw e
            }
            collectedImages(chapterDir)
        }

    suspend fun releaseArchive(archiveFile: File) = withContext(Dispatchers.IO) {
        File(rootDir, Hashing.sha256(archiveFile.absolutePath)).deleteRecursively()
        Unit
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        rootDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        rootDir.deleteRecursively()
        rootDir.mkdirs()
        Unit
    }

    private fun collectedImages(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && ImageSupport.isImage(f.name) }
            ?.sortedBy { it.name }
            .orEmpty()

    companion object {
        private const val DIR_NAME = "extracted_chapters"
    }
}
