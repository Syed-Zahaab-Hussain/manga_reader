package com.example.mangareader.scanner

import com.example.mangareader.core.io.ImageSupport
import com.example.mangareader.core.natural.NaturalSort
import com.example.mangareader.domain.model.ChapterItem
import com.example.mangareader.domain.model.MangaItem
import com.example.mangareader.domain.model.ScanWarning
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.File

sealed interface ScanEvent {
    data class Progress(val current: Int, val total: Int) : ScanEvent
    data class Batch(val items: List<MangaItem>) : ScanEvent
    data class Warning(val warning: ScanWarning) : ScanEvent
    data object Completed : ScanEvent
}

class MangaScanner {

    fun scan(root: File, batchSize: Int = DEFAULT_BATCH_SIZE): Flow<ScanEvent> = flow {
        val entries = root.listFiles()
            ?.filter { !it.name.startsWith(".") }
            .orEmpty()
        val mangaDirs = NaturalSort.sortedByString(entries.filter { it.isDirectory }) { it.name }
        val archiveFiles = NaturalSort.sortedByString(
            entries.filter { it.isFile && ImageSupport.isSupportedArchive(it.name) }
        ) { it.name }
        val total = mangaDirs.size + archiveFiles.size
        if (total == 0) {
            emit(ScanEvent.Completed)
            return@flow
        }

        emit(ScanEvent.Progress(0, total))
        var processed = 0
        val batch = ArrayList<MangaItem>(batchSize)

        suspend fun flushBatch() {
            if (batch.isNotEmpty()) {
                emit(ScanEvent.Batch(batch.toList()))
                batch.clear()
            }
        }

        for (dir in mangaDirs) {
            currentCoroutineContext().ensureActive()
            processed++
            emit(ScanEvent.Progress(processed, total))
            scanFolderManga(root, dir)?.let { item ->
                batch.add(item)
                if (batch.size >= batchSize) flushBatch()
            } ?: run {
                emit(
                    ScanEvent.Warning(
                        ScanWarning(dir.name, "No supported images found")
                    )
                )
            }
        }

        for (archive in archiveFiles) {
            currentCoroutineContext().ensureActive()
            processed++
            emit(ScanEvent.Progress(processed, total))
            val inspection = ArchiveSource.inspect(archive)
            inspection.fold(
                onSuccess = { content ->
                    batch.add(buildArchiveItem(archive, content))
                    if (batch.size >= batchSize) flushBatch()
                },
                onFailure = { error ->
                    emit(
                        ScanEvent.Warning(
                            ScanWarning(archive.name, error.message ?: "Unreadable archive")
                        )
                    )
                }
            )
        }

        flushBatch()
        emit(ScanEvent.Completed)
    }.flowOn(Dispatchers.IO)

    private fun scanFolderManga(root: File, dir: File): MangaItem? {
        val children = dir.listFiles().orEmpty()
        val subDirs = NaturalSort.sortedByString(children.filter { it.isDirectory }) { it.name }
        val looseImages = children.filter { it.isFile && ImageSupport.isImage(it.name) }

        return if (subDirs.isNotEmpty()) {
            val chapters = subDirs.mapIndexed { index, chapterDir ->
                ChapterItem(
                    index = index,
                    title = chapterDir.name,
                    path = chapterDir.absolutePath,
                    pageCount = countImages(chapterDir),
                    isArchiveChapter = false,
                    archivePath = null,
                    archivePrefix = null
                )
            }.filter { it.pageCount > 0 }
            if (chapters.isEmpty()) return null
            MangaItem(
                id = dir.toRelativeString(root),
                title = dir.name,
                rootPath = dir.absolutePath,
                isArchive = false,
                coverPath = CoverDetector.detect(dir, subDirs),
                coverArchivePath = null,
                coverEntryName = null,
                chapters = chapters
            )
        } else if (looseImages.isNotEmpty()) {
            MangaItem(
                id = dir.toRelativeString(root),
                title = dir.name,
                rootPath = dir.absolutePath,
                isArchive = false,
                coverPath = CoverDetector.detect(dir, emptyList()),
                coverArchivePath = null,
                coverEntryName = null,
                chapters = listOf(
                    ChapterItem(
                        index = 0,
                        title = dir.name,
                        path = dir.absolutePath,
                        pageCount = looseImages.size,
                        isArchiveChapter = false,
                        archivePath = null,
                        archivePrefix = null
                    )
                )
            )
        } else {
            null
        }
    }

    private fun buildArchiveItem(archiveFile: File, inspection: ArchiveSource.Inspection): MangaItem =
        MangaItem(
            id = archiveFile.name,
            title = archiveFile.nameWithoutExtension,
            rootPath = archiveFile.absolutePath,
            isArchive = true,
            coverPath = null,
            coverArchivePath = archiveFile.absolutePath,
            coverEntryName = inspection.coverEntryName,
            chapters = inspection.chapters.mapIndexed { index, spec ->
                ChapterItem(
                    index = index,
                    title = spec.title,
                    path = "",
                    pageCount = spec.imageCount,
                    isArchiveChapter = true,
                    archivePath = archiveFile.absolutePath,
                    archivePrefix = spec.prefix
                )
            }
        )

    private fun countImages(dir: File): Int =
        dir.listFiles { f -> f.isFile && ImageSupport.isImage(f.name) }?.size ?: 0

    companion object {
        const val DEFAULT_BATCH_SIZE = 5
    }
}
