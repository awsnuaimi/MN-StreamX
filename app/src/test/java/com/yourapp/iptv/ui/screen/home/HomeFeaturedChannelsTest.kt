package com.yourapp.iptv.ui.screen.home

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeDashboardSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeaturedChannelsTest {
    private fun channel(id: Int) = Channel(id.toString(), "Channel $id",
        streamUrl = "https://example.org/$id.m3u8")

    @Test fun recentIsPreferredAndShownBeforeHero() {
        val recent = listOf(channel(4), channel(3))
        val result = HomeFeaturedChannels.select(
            HomeDashboardSelection(recent, listOf(channel(2)), recent.first()),
            listOf(channel(1)))
        assertEquals(HomeFeaturedKind.RECENT, result.kind)
        assertEquals(listOf("4", "3"), result.channels.map { it.id })
    }

    @Test fun favoritesAppearWhenHistoryIsEmpty() {
        val favorites = listOf(channel(7))
        val result = HomeFeaturedChannels.select(
            HomeDashboardSelection(emptyList(), favorites, favorites.first()),
            listOf(channel(1)))
        assertEquals(HomeFeaturedKind.FAVORITES, result.kind)
        assertEquals("7", result.channels.single().id)
    }

    @Test fun availableChannelsAreShownWhenNoHistoryOrFavoritesExist() {
        val result = HomeFeaturedChannels.select(
            HomeDashboardSelection(emptyList(), emptyList(), null),
            (1..30).map(::channel))
        assertEquals(HomeFeaturedKind.DISCOVER, result.kind)
        assertEquals(listOf("1", "2", "3", "4", "5"), result.channels.map { it.id })
    }

    @Test fun duplicatesAreRemovedAndLimitIsRespected() {
        val result = HomeFeaturedChannels.select(
            HomeDashboardSelection(emptyList(), emptyList(), null),
            listOf(channel(1), channel(1), channel(2), channel(3)), limit = 2)
        assertEquals(listOf("1", "2"), result.channels.map { it.id })
    }

    @Test fun noChannelsReturnsEmptyWithoutPlaceholderStream() {
        val result = HomeFeaturedChannels.select(
            HomeDashboardSelection(emptyList(), emptyList(), null), emptyList())
        assertTrue(result.channels.isEmpty())
        assertEquals(HomeFeaturedKind.DISCOVER, result.kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLimitIsRejected() {
        HomeFeaturedChannels.select(HomeDashboardSelection(emptyList(), emptyList(), null),
            emptyList(), 0)
    }
}