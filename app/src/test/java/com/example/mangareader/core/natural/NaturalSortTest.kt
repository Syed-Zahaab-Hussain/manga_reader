package com.example.mangareader.core.natural

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalSortTest {

    @Test
    fun `numeric chapter names use natural order`() {
        assertTrue(NaturalSort.compare("Chapter 2", "Chapter 10") < 0)
    }

    @Test
    fun `comparison is case insensitive`() {
        assertEquals(0, NaturalSort.compare("ch1", "Ch1"))
    }

    @Test
    fun `leading zero number remains numerically ordered`() {
        assertTrue(NaturalSort.compare("001", "2") < 0)
    }

    @Test
    fun `list sorting preserves numeric sequence`() {
        val values = listOf("Chapter 10", "Chapter 1", "Chapter 2")

        assertEquals(
            listOf("Chapter 1", "Chapter 2", "Chapter 10"),
            NaturalSort.sortedByString(values) { it }
        )
    }
}
