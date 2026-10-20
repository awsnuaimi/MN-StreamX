package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class ChannelFavoritesTest {
    private val channels = listOf(
        Channel("a", "Alpha", streamUrl = "https://unit.test/a", group = "News"),
        Channel("b", "Beta", streamUrl = "https://unit.test/b", group = "News"),
        Channel("c", "Gamma", streamUrl = "https://unit.test/c", group = "Sport"))

    @Test fun unknownFavoriteStatePreservesTheDatabaseSnapshot() {
        val existing = channels.map { it.copy(isFavorite = true) }
        assertSame(existing, ChannelFavorites.apply(existing, null))
        assertTrue(existing.all { it.isFavorite })
    }

    @Test fun emptyAndUnrelatedIdsDoNotInventFavoritesOrDeleteChannels() {
        val result = ChannelFavorites.apply(channels, setOf("old-source-channel"))
        assertEquals(channels, result)
        assertEquals(0, PlaybackQueue.create(result, favoritesOnly = true).size)
    }

    @Test fun unchangedFavoritesReuseTheListAndChangesPreserveUnmodifiedRows() {
        assertSame(channels, ChannelFavorites.apply(channels, emptySet()))
        val changed = ChannelFavorites.apply(channels, setOf("b"))
        assertSame(channels[0], changed[0])
        assertSame(channels[2], changed[2])
        assertTrue(changed[1].isFavorite)
        assertFalse(channels[1].isFavorite)
        assertSame(changed, ChannelFavorites.apply(changed, setOf("b")))
    }

    @Test fun favoritesPreserveOrderAndIntersectCategoryAndSearch() {
        val marked = ChannelFavorites.apply(channels + channels.first(), setOf("a", "c"))
        assertEquals(listOf("a", "c"), PlaybackQueue.create(marked, favoritesOnly = true).channels.map { it.id })
        assertEquals(listOf("a"), PlaybackQueue.create(marked, "News", " ALP ", true).channels.map { it.id })
        assertEquals(3, PlaybackQueue.create(marked).size)
    }

    @Test fun removedCurrentChannelUsesOriginalOrderAndWrapsBothWays() {
        val original = PlaybackQueue.create(channels)
        val remaining = PlaybackQueue.create(ChannelFavorites.apply(channels, setOf("a", "c")), favoritesOnly = true)
        assertEquals("c", remaining.adjacentIdFrom("b", 1, original))
        assertEquals("a", remaining.adjacentIdFrom("b", -1, original))
        assertEquals("a", remaining.adjacentIdFrom("c", 1, original))
        assertEquals("c", remaining.adjacentIdFrom("a", -1, original))
        assertNull(PlaybackQueue.empty().adjacentIdFrom("b", 1, original))
    }
}