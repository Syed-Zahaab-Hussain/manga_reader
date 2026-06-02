package com.example.mangareader.core.io

import java.util.Locale

object ImageSupport {

    val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

    fun isImage(fileName: String): Boolean =
        imageExtensions.contains(extensionOf(fileName))

    fun isCoverLike(fileName: String): Boolean =
        fileName.substringBeforeLast('.', "").lowercase(Locale.US).startsWith("cover")

    fun isSupportedArchive(fileName: String): Boolean =
        extensionOf(fileName) in setOf("zip", "cbz")

    private fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase(Locale.US)
}
