package com.example.mangareader.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderInteractionTest {

    @Test
    fun `zoom is capped at four times`() {
        assertEquals(4f, calculateZoomScale(currentScale = 3f, zoomChange = 2f), 0f)
    }

    @Test
    fun `zoom cannot shrink below original size`() {
        assertEquals(1f, calculateZoomScale(currentScale = 1.5f, zoomChange = 0.5f), 0f)
    }
}
