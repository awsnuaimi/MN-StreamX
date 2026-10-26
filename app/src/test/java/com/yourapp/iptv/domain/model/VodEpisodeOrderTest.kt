package com.yourapp.iptv.domain.model

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class VodEpisodeOrderTest {
    private fun episode(id: String, number: Int, season: Int = 1) = VodEpisode(id,"Episode $id",season,number,"mp4")
    @Test fun nextFollowsEpisodeNumbersAndCrossesTheSeasonBoundary() {
        val rows = VodEpisodeOrder.build(listOf(VodSeason(2,"Two",listOf(episode("40",1,2))),
            VodSeason(1,"One",listOf(episode("30",3),episode("10",1),episode("20",2)))))
        assertEquals(listOf("10","20","30","40"),rows.map { it.id })
        assertEquals("20",VodEpisodeOrder.next(rows,"10")?.id)
        assertEquals("40",VodEpisodeOrder.next(rows,"30")?.id)
        assertNull(VodEpisodeOrder.next(rows,"40"))
        assertNull(VodEpisodeOrder.next(rows,"999"))
    }
    @Test fun duplicateProviderIdsCannotCreateAnEpisodeLoop() {
        val rows = VodEpisodeOrder.build(listOf(VodSeason(1,"One",listOf(episode("10",1),episode("20",2),episode("10",3),episode("30",4)))))
        assertEquals(listOf("10","20","30"),rows.map { it.id })
        assertEquals("30",VodEpisodeOrder.next(rows,"20")?.id)
    }
    @Test fun unsupportedAndMalformedEntriesAreNotPlaybackTargets() {
        val rows = VodEpisodeOrder.build(listOf(VodSeason(1,"One",listOf(episode("bad",1),
            episode("10",2).copy(extension = "exe"),episode("20",3)))))
        assertEquals(listOf("20"),rows.map { it.id })
        assertFalse(VodEpisodeOrder.validId("")); assertFalse(VodEpisodeOrder.validId("1/2"))
    }
    @Test fun providerOrderIsStableForEqualEpisodeNumbersAndParentSeasonWins() {
        val rows = VodEpisodeOrder.build(listOf(VodSeason(2,"Two",listOf(episode("20",1),episode("10",1)))))
        assertEquals(listOf("20","10"),rows.map { it.id }); assertTrue(rows.all { it.season == 2 })
    }
    @Test fun preparingLargeMetadataHonorsCancellation() {
        var checks = 0
        try {
            VodEpisodeOrder.build(listOf(VodSeason(1,"One",(1..1000).map { episode(it.toString(),it) }))) {
                checks++; throw CancellationException()
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { assertEquals(1,checks) }
    }
}