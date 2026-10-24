package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class VodProgressTest {
    private fun entry(id: String = "123", kind: VodKind = VodKind.MOVIE,
        position: Long = 40_000, duration: Long = 100_000, updated: Long = 5L) =
        VodProgressEntry(kind, id, "A title", "mp4", position, duration, updated)

    @Test fun interruptedMovieCanResumeButCompletedMovieDisappears() {
        val saved = VodProgress.record(emptyList(), entry())
        assertEquals(40_000L, VodProgress.find(saved, VodKind.MOVIE, "123")?.positionMs)
        assertTrue(VodProgress.record(saved, entry(position = 99_000), completed = true).isEmpty())
    }

    @Test fun nearEndOrTooEarlyNeverAppearsAsIncomplete() {
        assertTrue(VodProgress.record(emptyList(), entry(position = 2_000)).isEmpty())
        assertTrue(VodProgress.record(emptyList(), entry(position = 72_000)).isEmpty())
        assertTrue(VodProgress.record(emptyList(), entry(position = 20_000, duration = 25_000)).isEmpty())
    }

    @Test fun episodesAndMoviesWithSameIdNeverOverwriteEachOther() {
        val movie = entry()
        val episode = entry(kind = VodKind.SERIES, updated = 10)
        val saved = VodProgress.record(VodProgress.record(emptyList(), movie), episode)
        assertEquals(2, saved.size)
        assertNotNull(VodProgress.find(saved, VodKind.MOVIE, "123"))
        assertNotNull(VodProgress.find(saved, VodKind.SERIES, "123"))
        assertEquals(1, VodProgress.record(saved, episode, completed = true).size)
    }

    @Test fun boundedAndSortedWithNewestFirst() {
        var rows = emptyList<VodProgressEntry>()
        (1..80).forEach { rows = VodProgress.record(rows, entry(id = it.toString(), updated = it.toLong())) }
        assertEquals(VodProgress.LIMIT, rows.size)
        assertEquals("80", rows.first().id)
        assertNull(VodProgress.find(rows, VodKind.MOVIE, "1"))
    }

    @Test fun malformedEntryCannotBeStored() {
        assertFalse(VodProgress.isValid(entry(id = "a/../../")))
        assertFalse(VodProgress.isValid(entry(id = "٣")))
        assertFalse(VodProgress.isValid(entry(position = -10)))
        assertTrue(runCatching { VodProgress.record(emptyList(), entry(id = "x")) }.isFailure)
    }
}