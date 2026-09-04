package com.easytv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaScannerTest {
    @Test
    fun `episode parser prioritizes explicit episode markers`() {
        assertEquals(1, MediaScanner.episodeNumber("第01集.mp4"))
        assertEquals(12, MediaScanner.episodeNumber("EP12.mkv"))
        assertEquals(7, MediaScanner.episodeNumber("E07 - 2024.avi"))
    }

    @Test
    fun `episode parser falls back to any filename number`() {
        assertEquals(3, MediaScanner.episodeNumber("西游记 03.mp4"))
        assertEquals(Int.MAX_VALUE, MediaScanner.episodeNumber("特别篇.mp4"))
    }
}
