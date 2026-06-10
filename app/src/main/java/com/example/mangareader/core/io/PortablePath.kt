package com.example.mangareader.core.io

import java.io.File

object PortablePath {
    fun relativeToRoot(root: File?, path: String?): String? {
        if (path.isNullOrBlank()) return null
        val normalized = path.replace('\\', '/')
        val isAbsolute = File(path).isAbsolute || normalized.startsWith('/')
        if (!isAbsolute) return normalized
        if (root == null) return null
        return runCatching {
            val rootPath = root.canonicalFile.toPath()
            val filePath = File(path).canonicalFile.toPath()
            if (filePath.startsWith(rootPath)) {
                rootPath.relativize(filePath).toString().replace('\\', '/')
            } else {
                null
            }
        }.getOrNull()
    }

    fun resolve(root: File?, path: String?): String? {
        if (root == null || path.isNullOrBlank()) return null
        val normalized = path.replace('\\', '/')
        val isAbsolute = File(path).isAbsolute || normalized.startsWith('/')
        if (isAbsolute) return null
        return resolveUnderRoot(root, normalized)
    }

    private fun resolveUnderRoot(root: File, relativePath: String): String? = runCatching {
        val rootFile = root.canonicalFile
        val resolved = File(rootFile, relativePath).canonicalFile
        resolved.takeIf { it.toPath().startsWith(rootFile.toPath()) }?.absolutePath
    }.getOrNull()
}
