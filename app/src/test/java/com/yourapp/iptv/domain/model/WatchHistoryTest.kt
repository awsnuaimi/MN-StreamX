package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class WatchHistoryTest {
    @Test fun keepsOnlyTwentyUniqueChannelsInLatestViewingOrder() {
        var rows = emptyList<WatchHistoryEntry>()
        repeat(100) { rows = WatchHistory.record(rows, WatchHistoryEntry("id-$it", it.toLong())) }
        assertEquals((99 downTo 80).map { "id-$it" }, rows.map { it.channelId })
        rows = WatchHistory.record(rows, WatchHistoryEntry("id-85", 200))
        assertEquals("id-85", rows.first().channelId)
        assertEquals(20, rows.size); assertEquals(20, rows.map { it.channelId }.distinct().size)
    }
    @Test fun changingTheWallClockCannotBuryANewViewingEvent() {
        val rows = WatchHistory.record(listOf(WatchHistoryEntry("old", 1000)), WatchHistoryEntry("new", 10))
        assertEquals("new", rows.first().channelId)
        assertTrue(rows.first().watchedAtMillis > rows.last().watchedAtMillis)
    }
    @Test fun projectionUsesCurrentMetadataAndSkipsDeletedChannels() {
        val channel = Channel("b", "Updated", streamUrl = "https://unit.test/new")
        val rows = WatchHistory.project(listOf(WatchHistoryEntry("deleted", 3), WatchHistoryEntry("b", 2)), mapOf("b" to channel))
        assertEquals(1, rows.size); assertSame(channel, rows.single().channel)
    }
    @Test fun blankChannelIdentifiersAreRejected() {
        assertTrue(runCatching { WatchHistory.record(emptyList(), WatchHistoryEntry("", 1)) }.isFailure)
    }
}