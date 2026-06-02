package com.example.mangareader.scanner

import com.example.mangareader.core.io.ImageSupport
import com.example.mangareader.core.natural.NaturalSort
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.File
import java.io.IOException

object ArchiveSource {

    data class ChapterSpec(
        val prefix: String,
        val title: String,
        val imageCount: Int
    )

    data class Inspection(
        val chapters: List<ChapterSpec>,
        val coverEntryName: String?
    )

    fun inspect(archiveFile: File): Result<Inspection> = runCatching {
        ZipFile(archiveFile).use { zip ->
            val headers = zip.fileHeaders
                ?: throw IOException("Unreadable archive")
            if (headers.any { !it.isDirectory && it.isEncrypted }) {
                throw IOException("Password-protected archive")
            }
            val imageNames = headers.asSequence()
                .filter { !it.isDirectory }
                .map { it.fileName.replace('\\', '/') }
                .filter { !it.contains("..") && ImageSupport.isImage(it.substringAfterLast('/')) }
                .sortedWith(NaturalSort.STRING_COMPARATOR)
                .toList()
            if (imageNames.isEmpty()) {
                throw IOException("No supported images")
            }

            val topLevelFolders = imageNames.asSequence()
                .filter { it.contains('/') }
                .map { it.substringBefore('/') }
                .distinct()
                .sortedWith(NaturalSort.STRING_COMPARATOR)
                .toList()

            val chapters: List<ChapterSpec> =
                if (topLevelFolders.isNotEmpty()) {
                    topLevelFolders.mapIndexed { index, folder ->
                        val prefix = "$folder/"
                        ChapterSpec(
                            prefix = prefix,
                            title = folder,
                            imageCount = imageNames.count { name ->
                                name.startsWith(prefix) && !name.removePrefix(prefix).contains('/')
                            }
                        )
                    }.filter { it.imageCount > 0 }
                } else {
                    listOf(
                        ChapterSpec(
                            prefix = "",
                            title = archiveFile.nameWithoutExtension,
                            imageCount = imageNames.size
                        )
                    )
                }

            if (chapters.isEmpty()) {
                throw IOException("No readable chapters")
            }

            val coverEntryName = imageNames.firstOrNull { name ->
                !name.contains('/') && ImageSupport.isCoverLike(name.substringAfterLast('/'))
            } ?: chapters.firstNotNullOfOrNull { chapter ->
                imageNames.firstOrNull { name ->
                    name.startsWith(chapter.prefix) &&
                        (chapter.prefix.isEmpty() || !name.removePrefix(chapter.prefix).contains('/'))
                }
            }

            Inspection(chapters = chapters, coverEntryName = coverEntryName)
        }
    }.recoverCatching { error ->
        throw IOException(
            error.message ?: "Corrupted or unsupported archive",
            error as? ZipException ?: error as? IOException
        )
    }
}
