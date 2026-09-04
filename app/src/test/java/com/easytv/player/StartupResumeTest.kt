package com.easytv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupResumeTest {
    @Test
    fun `startup resumes the most recently watched unfinished episode`() {
        val older = series(id = 1, episodeId = 11, position = 20_000, duration = 100_000, updatedAt = 100)
        val newer = series(id = 2, episodeId = 22, position = 30_000, duration = 100_000, updatedAt = 200)

        val target = findLatestResume(listOf(older, newer))

        assertEquals(2L, target?.series?.id)
        assertEquals(1, target?.episodeIndex)
    }

    @Test
    fun `startup ignores completed and orphaned history`() {
        val completed = series(id = 1, episodeId = 11, position = 96_000, duration = 100_000, updatedAt = 300)
        val orphaned = series(id = 2, episodeId = 99, position = 20_000, duration = 100_000, updatedAt = 400)

        assertNull(findLatestResume(listOf(completed, orphaned)))
    }

    private fun series(id: Long, episodeId: Long, position: Long, duration: Long, updatedAt: Long): Series {
        val episodes = listOf(
            Episode(id * 10 + 1, id, "file:///episode1.mp4", "episode1.mp4", 1, 20_000_000),
            Episode(id * 10 + 2, id, "file:///episode2.mp4", "episode2.mp4", 2, 20_000_000),
        )
        return Series(
            id = id,
            directoryUri = "/series/$id",
            name = "Series $id",
            posterUri = null,
            episodes = episodes,
            history = PlayHistory(id, episodeId, position, duration, updatedAt),
        )
    }
}
