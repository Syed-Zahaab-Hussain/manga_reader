package com.example.mangareader.scanner

import com.example.mangareader.core.io.ImageSupport
import com.example.mangareader.core.natural.NaturalSort
import java.io.File

object CoverDetector {

    fun detect(
        mangaDir: File,
        chapterDirs: List<File>
    ): String? {
        val looseImages = imagesIn(mangaDir)
        return looseImages.firstOrNull { ImageSupport.isCoverLike(it.name) }?.absolutePath
            ?: chapterDirs.firstNotNullOfOrNull { chapter -> imagesIn(chapter).firstOrNull()?.absolutePath }
            ?: looseImages.firstOrNull { !ImageSupport.isCoverLike(it.name) }?.absolutePath
            ?: looseImages.firstOrNull()?.absolutePath
    }

    fun firstImageAbsolutePath(chapterDir: File): String? =
        imagesIn(chapterDir).firstOrNull()?.absolutePath

    private fun imagesIn(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && ImageSupport.isImage(f.name) }
            ?.sortedWith(compareBy(NaturalSort.STRING_COMPARATOR) { it.name })
            .orEmpty()
}
