package com.easytv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFiltersTest {
    @Test
    fun `ass vector drawing is hidden while normal dialogue remains`() {
        assertTrue(isAssDrawingText("{\\p1}m 2.39 33.02 b 2.6 33.37 4.1 34.2 l 5 35 6 36"))
        assertFalse(isAssDrawingText("我们回家吧。"))
    }

    @Test
    fun `short text that happens to contain coordinates is not hidden`() {
        assertFalse(isAssDrawingText("m 2 3"))
    }
}
