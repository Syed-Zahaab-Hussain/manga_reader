package com.example.mangareader.data.reader

import com.example.mangareader.core.io.ImageSupport
import com.example.mangareader.core.natural.NaturalSort
import com.example.mangareader.data.cache.ArchiveExtractCache
import com.example.mangareader.domain.model.ChapterItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ChapterPageRepository(
    private val archiveExtractCache: ArchiveExtractCache
) {

    suspend fun loadPages(chapter: ChapterItem): List<File> {
        val pages = if (chapter.isArchiveChapter) {
            val archive = chapter.archivePath
                ?.let(::File)
                ?.takeIf { it.isFile && it.canRead() }
                ?: throw IOException("The manga archive is missing or cannot be read.")
            archiveExtractCache.extractChapter(archive, chapter.archivePrefix.orEmpty())
        } else {
            withContext(Dispatchers.IO) {
                val folder = File(chapter.path)
                if (!folder.isDirectory || !folder.canRead()) {
                    throw IOException("The chapter folder is missing or cannot be read.")
                }
                NaturalSort.sortedByString(
                    folder.listFiles { file -> file.isFile && ImageSupport.isImage(file.name) }
                        .orEmpty()
                        .toList()
                ) { it.name }
            }
        }
        if (pages.isEmpty()) throw IOException("This chapter contains no supported images.")
        return pages
    }
}
