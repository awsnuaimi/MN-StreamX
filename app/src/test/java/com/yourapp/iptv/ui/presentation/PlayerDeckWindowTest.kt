package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.Channel
import org.junit.Assert.*
import org.junit.Test

class PlayerDeckWindowTest {
    private val channels = (1..100).map { Channel("$it", "Channel $it", null, "https://unit.test/$it") }

    @Test fun middleWindowIncludesCurrentChannelAndKeepsQueueNumbers() {
        val items = PlayerDeckWindow.channels(channels, 50)
        assertEquals((46..55).toList(), items.map { it.number })
        assertTrue(items.any { it.channel.id == "51" })
        assertEquals(10, items.size)
    }

    @Test fun beginningAndEndDoNotWrapOrDuplicateCards() {
        assertEquals((1..10).toList(), PlayerDeckWindow.channels(channels, 0).map { it.number })
        assertEquals((91..100).toList(), PlayerDeckWindow.channels(channels, 99).map { it.number })
    }

    @Test fun detachedPlaybackAndInvalidIndexesUseASafeWindow() {
        for (index in listOf(-1, Int.MAX_VALUE)) {
            assertEquals((1..10).toList(), PlayerDeckWindow.channels(channels, index).map { it.number })
        }
    }

    @Test fun emptyAndShortQueuesDoNotCreatePlaceholderChannels() {
        assertTrue(PlayerDeckWindow.channels(emptyList(), -1).isEmpty())
        assertEquals(listOf("1"), PlayerDeckWindow.channels(channels.take(1), 0).map { it.channel.id })
        assertEquals(3, PlayerDeckWindow.channels(channels.take(3), 2).size)
    }

    @Test fun cardCountIsBoundedAndOriginalQueueIsNotModified() {
        assertEquals(12, PlayerDeckWindow.channels(channels, 50, Int.MAX_VALUE).size)
        assertEquals(1, PlayerDeckWindow.channels(channels, 50, -1).size)
        assertEquals((1..100).map { "$it" }, channels.map { it.id })
    }

    @Test fun remainingTimeRoundsUpAndDisappearsAtTheEnd() {
        assertEquals("متبقٍ 1 د", PlayerDeckWindow.remaining(60_001, 60_000))
        assertEquals("متبقٍ 2 د", PlayerDeckWindow.remaining(120_001, 60_000))
        assertNull(PlayerDeckWindow.remaining(60_000, 60_000))
        assertNull(PlayerDeckWindow.remaining(59_000, 60_000))
    }
}