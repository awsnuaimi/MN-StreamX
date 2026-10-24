package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class HomeDashboardSelectionTest {
    private val channels = listOf(
        Channel("a", "Alpha", streamUrl = "https://example.test/a", isFavorite = true),
        Channel("b", "Beta", streamUrl = "https://example.test/b"),
        Channel("c", "Gamma", streamUrl = "https://example.test/c", isFavorite = true)
    )

    @Test fun lastWatchedChannelsFollowHistoryOrderAndSkipMissingEntries() {
        val result = HomeDashboardSelection.project(channels, listOf("deleted", "c", "b", "c", "a"))
        assertEquals(listOf("c", "b", "a"), result.recent.map { it.id })
        assertEquals("c", result.highlighted?.id)
    }

    @Test fun favoritesAreSourceCatalogBoundedAndPreserveOriginalOrder() {
        val result = HomeDashboardSelection.project(channels, listOf("from-old-account"))
        assertTrue(result.recent.isEmpty())
        assertEquals(listOf("a", "c"), result.favorites.map { it.id })
        assertEquals("a", result.highlighted?.id)
    }

    @Test fun anEmptyCatalogNeverCreatesAnUnplayableTile() {
        val result = HomeDashboardSelection.project(emptyList(), listOf("a"))
        assertNull(result.highlighted)
        assertTrue(result.recent.isEmpty())
        assertTrue(result.favorites.isEmpty())
    }

    @Test fun limitCapsBothListsAndRejectsUnboundedLimits() {
        val result = HomeDashboardSelection.project(channels, listOf("c", "b", "a"), 1)
        assertEquals(listOf("c"), result.recent.map { it.id })
        assertEquals(listOf("a"), result.favorites.map { it.id })
        assertThrows(IllegalArgumentException::class.java) {
            HomeDashboardSelection.project(channels, emptyList(), 200)
        }
    }
}