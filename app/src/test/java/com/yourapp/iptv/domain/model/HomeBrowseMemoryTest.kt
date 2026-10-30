package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class HomeBrowseMemoryTest {
    @Test fun eachCategoryAndFavoritesKeepIndependentChannelsAndOffsets() {
        val memory = HomeBrowseMemory()
        val news = HomeBrowseSession("News", focusedChannelId = "n40", position = HomeGridPosition("n37", 12, 1))
        val sports = HomeBrowseSession("Sport", focusedChannelId = "s50", position = HomeGridPosition("s47", 6, 1))
        memory.remember(news); memory.remember(sports)
        memory.remember(news.copy(favoritesOnly = true, focusedChannelId = "favorite"))
        assertEquals(news.focusedChannelId, memory.find("News", false)?.focusedChannelId)
        assertEquals(news.position, memory.find("News", false)?.position)
        assertEquals(sports.position, memory.find("Sport", false)?.position)
        assertEquals("favorite", memory.find("News", true)?.focusedChannelId)
    }

    @Test fun legacySessionIsImportedAndSourceResetDropsEveryBookmark() {
        val memory = HomeBrowseMemory()
        memory.load(HomeBrowseSession("News", focusedChannelId = "old"))
        assertEquals("old", memory.find("News", false)?.focusedChannelId)
        memory.clear()
        assertNull(memory.find("News", false))
    }

    @Test fun memoryEvictsTheLeastRecentlyUsedCategoryAndNeverStoresAnEmptyFocus() {
        val memory = HomeBrowseMemory()
        repeat(32) { memory.remember(HomeBrowseSession("$it", focusedChannelId = "c$it")) }
        memory.remember(HomeBrowseSession("0", focusedChannelId = "new0"))
        memory.remember(HomeBrowseSession("new", focusedChannelId = "new"))
        memory.remember(HomeBrowseSession("empty"))
        assertEquals(32, memory.decorate(HomeBrowseSession()).bookmarks.size)
        assertNull(memory.find("1", false)); assertNull(memory.find("empty", false))
        assertEquals("new0", memory.find("0", false)?.focusedChannelId)
    }

    @Test fun exportingAndLoadingPreservesThePerCategoryHistory() {
        val memory = HomeBrowseMemory()
        memory.remember(HomeBrowseSession("A", focusedChannelId = "a"))
        val saved = memory.remember(HomeBrowseSession("B", focusedChannelId = "b"))
        val restored = HomeBrowseMemory().apply { load(saved) }
        assertEquals("a", restored.find("A", false)?.focusedChannelId)
        assertEquals("b", restored.find("B", false)?.focusedChannelId)
    }
}