package com.easytv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlTest {
    @Test
    fun `selection moves across enabled playback controls`() {
        assertEquals(0, movePlayerControl(1, -1, hasPrevious = true, hasNext = true))
        assertEquals(2, movePlayerControl(1, 1, hasPrevious = true, hasNext = true))
    }

    @Test
    fun `selection skips unavailable episode controls`() {
        assertEquals(1, movePlayerControl(1, -1, hasPrevious = false, hasNext = true))
        assertEquals(1, movePlayerControl(1, 1, hasPrevious = true, hasNext = false))
    }
}
