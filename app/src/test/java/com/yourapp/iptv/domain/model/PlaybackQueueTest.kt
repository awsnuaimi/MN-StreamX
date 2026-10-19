package com.yourapp.iptv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueTest {
    private val channels = listOf(
        Channel("a", "Alpha News", streamUrl = "https://unit.test/a", group = "News"),
        Channel("b", "Beta News", streamUrl = "https://unit.test/b", group = "News"),
        Channel("c", "Alpha Sports", streamUrl = "https://unit.test/c", group = "Sports")
    )

    @Test fun categoryAndSearchKeepTheDisplayedOrder() {
        val queue = PlaybackQueue.create(channels, group = "News", query = "  NEWS ")
        assertEquals(listOf("a", "b"), queue.channels.map { it.id })
        assertEquals(1, queue.indexOf("b"))
        assertEquals(listOf("a"), PlaybackQueue.create(channels, "News", "alpha").channels.map { it.id })
    }

    @Test fun navigationWrapsAtBothEnds() {
        val queue = PlaybackQueue.create(channels)
        assertEquals(0, queue.adjacentIndex(2, 1))
        assertEquals(2, queue.adjacentIndex(0, -1))
    }

    @Test fun emptyAndMissingSelectionNeverChooseAnUnrelatedChannel() {
        assertEquals(-1, PlaybackQueue.empty().adjacentIndex(0, 1))
        val queue = PlaybackQueue.create(channels)
        assertEquals(-1, queue.indexOf("missing"))
        assertEquals(-1, queue.adjacentIndex(-1, 1))
        assertEquals(0, PlaybackQueue.create(channels.take(1)).adjacentIndex(0, -1))
    }

    @Test fun duplicateIdsDoNotCreateAmbiguousNavigationPositions() {
        val queue = PlaybackQueue.create(channels + channels.first().copy(name = "Duplicate"))
        assertEquals(3, queue.size)
        assertEquals("Alpha News", queue.channels.first().name)
        assertEquals(0, queue.indexOf("a"))
    }
}