package com.example.mangareader.core.io

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PortablePathTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "manga-reader-root")

    @Test
    fun `cover inside root is stored relatively`() {
        val cover = File(root, "Naruto/cover.jpg")

        assertEquals("Naruto/cover.jpg", PortablePath.relativeToRoot(root, cover.absolutePath))
    }

    @Test
    fun `relative cover resolves under current root`() {
        val resolved = PortablePath.resolve(root, "Naruto/cover.jpg")

        assertEquals(File(root, "Naruto/cover.jpg").absolutePath, resolved)
    }

    @Test
    fun `cover outside selected root is not stored`() {
        val outsideCover = File(root.parentFile, "outside/cover.jpg")

        assertEquals(null, PortablePath.relativeToRoot(root, outsideCover.absolutePath))
    }

    @Test
    fun `absolute references are not resolved`() {
        val absoluteCover = File(root, "Naruto/cover.jpg")

        assertEquals(null, PortablePath.resolve(root, absoluteCover.absolutePath))
    }

    @Test
    fun `path traversal is not resolved outside selected root`() {
        val resolved = PortablePath.resolve(root, "../private/cover.jpg")

        assertEquals(null, resolved)
    }
}
