package com.yourapp.iptv.domain.model

import com.yourapp.iptv.domain.organization.*
import org.junit.Assert.*
import org.junit.Test

class HomeSessionResolverTest {
    private val rows = listOf(channel("a", "polish", "news"), channel("b", "polish", "news"),
        channel("c", "german", "sport"))
    private val tree = OrganizationTree(true, listOf(ChannelFolder("polish", label = FolderLabel.POLISH),
        ChannelFolder("german", label = FolderLabel.GERMAN), ChannelFolder("news", "polish", name = "News"),
        ChannelFolder("sport", "german", name = "Sport"))).withCounts(rows)

    @Test fun keepsExistingGroupFocusAndAnchorAfterTheChannelOrderChanges() {
        val saved = HomeBrowseSession(FolderFilter.of("news"), "polish", focusedChannelId = "b",
            position = HomeGridPosition("a", 17, 3))
        val result = HomeSessionResolver.resolve(saved, rows.reversed(), tree)
        assertEquals(listOf("b", "a"), result.visible.map { it.id })
        assertEquals(saved, result.session)
    }

    @Test fun missingFocusedChannelFallsBackToAnExistingAnchor() {
        val saved = HomeBrowseSession(FolderFilter.of("news"), "polish", focusedChannelId = "removed",
            position = HomeGridPosition("b", 9, 3))
        assertEquals("b", HomeSessionResolver.resolve(saved, rows, tree).session.focusedChannelId)
    }

    @Test fun missingAnchorAndFocusChooseTheFirstAvailableChannelWithoutAnOldOffset() {
        val saved = HomeBrowseSession(FolderFilter.of("news"), "polish", focusedChannelId = "gone",
            position = HomeGridPosition("gone-too", 120, 3))
        val restored = HomeSessionResolver.resolve(saved, rows, tree).session
        assertEquals("a", restored.focusedChannelId)
        assertEquals("a", restored.position.anchorId)
        assertEquals(0, restored.position.offsetPx)
    }

    @Test fun removedFolderFallsBackToTheSameRoot() {
        val saved = HomeBrowseSession(FolderFilter.of("deleted"), "polish", focusedChannelId = "c")
        val result = HomeSessionResolver.resolve(saved, rows, tree)
        assertEquals(FolderFilter.of("polish"), result.session.group)
        assertEquals(listOf("a", "b"), result.visible.map { it.id })
        assertEquals("a", result.session.focusedChannelId)
    }

    @Test fun missingRootOrDisabledOrganizationFallsBackToAllChannels() {
        val saved = HomeBrowseSession(FolderFilter.of("missing"), "also-missing")
        assertNull(HomeSessionResolver.resolve(saved, rows, tree).session.group)
        assertNull(HomeSessionResolver.resolve(saved, rows, OrganizationTree()).session.group)
    }

    @Test fun rawProviderGroupIsPreservedWhenOrganizationIsDisabled() {
        val result = HomeSessionResolver.resolve(HomeBrowseSession("raw-news", focusedChannelId = "b"),
            rows, OrganizationTree())
        assertEquals("raw-news", result.session.group)
        assertEquals(listOf("a", "b"), result.visible.map { it.id })
    }

    @Test fun anOldRawGroupMapsToItsCurrentFolderWhenOrganizationWasEnabledLater() {
        val restored = HomeSessionResolver.resolve(HomeBrowseSession("raw-news", focusedChannelId = "b"), rows, tree)
        assertEquals(FolderFilter.of("news"), restored.session.group)
        assertEquals("b", restored.session.focusedChannelId)
    }

    @Test fun removedFavoritesDoNotLeaveTheRestoredScreenEmpty() {
        val result = HomeSessionResolver.resolve(HomeBrowseSession(FolderFilter.of("news"), "polish", true), rows, tree)
        assertFalse(result.session.favoritesOnly)
        assertEquals(FolderFilter.of("news"), result.session.group)
        assertEquals(2, result.visible.size)
    }

    @Test fun currentFavoritesAreUsedAndAnEmptyCatalogIsSafe() {
        val marked = rows.map { it.copy(isFavorite = it.id == "b") }
        val restored = HomeSessionResolver.resolve(HomeBrowseSession(favoritesOnly = true), marked, tree)
        assertTrue(restored.session.favoritesOnly)
        assertEquals("b", restored.session.focusedChannelId)
        val empty = HomeSessionResolver.resolve(restored.session, emptyList(), OrganizationTree())
        assertTrue(empty.visible.isEmpty()); assertNull(empty.session.focusedChannelId)
    }

    @Test fun explicitEmptyFavoritesKeepTheChosenCategoryInsteadOfSwitchingToAllChannels() {
        val group = FolderFilter.of("news")
        val result = HomeSessionResolver.restoreContext(HomeBrowseSession(group, "polish", true,
            focusedChannelId = "removed"), rows, tree)
        assertEquals(group, result.session.group)
        assertTrue(result.session.favoritesOnly)
        assertTrue(result.visible.isEmpty())
        assertNull(result.session.focusedChannelId)
    }

    @Test fun contextRestorationChoosesAnAvailableChannelWithinTheRequestedCategory() {
        val group = FolderFilter.of("news")
        val result = HomeSessionResolver.restoreContext(HomeBrowseSession(group, "polish",
            focusedChannelId = "gone", position = HomeGridPosition("gone", 50, 1)), rows, tree)
        assertEquals(group, result.session.group)
        assertEquals("a", result.session.focusedChannelId)
        assertEquals(0, result.session.position.offsetPx)
    }

    private fun channel(id: String, root: String, leaf: String) = Channel(id, id,
        streamUrl = "https://unit.test/$id", group = "raw-$leaf", folderIds = listOf(root, leaf))
}