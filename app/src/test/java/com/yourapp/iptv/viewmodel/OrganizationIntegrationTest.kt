package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.yourapp.iptv.domain.organization.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun homeGuideAndPlayerShareFolderScopeAndFavoritesKeepPlayingWhenRemoved() = runTest(dispatcher) {
        val channels = OrganizationChannelsFixture(); val organization = OrganizationMemory()
        val favorites = FakeFavoriteRepository(setOf("s2", "e1"))
        val home = HomeViewModel(OrganizationSettingsFixture(), OrganizationPlaylistsFixture(channels), dispatcher, favorites, organization)
        val args = mapOf("group" to FolderFilter.of("arabic"), "channelId" to "s2", "favoritesOnly" to true)
        val epg = EpgViewModel(SavedStateHandle(args), channels, dispatcher, favorites, organization)
        val player = PlayerViewModel(SavedStateHandle(args), channels, dispatcher, favorites, organization)
        advanceUntilIdle()
        home.selectSection(true); home.selectGroup(FolderFilter.of("arabic")); advanceUntilIdle()
        assertEquals(setOf("s2", "e1"), home.uiState.value.visibleChannels.map { it.id }.toSet())
        assertEquals(setOf("s2", "e1"), epg.uiState.value.queue.channels.map { it.id }.toSet())
        assertEquals(setOf("s2", "e1"), player.uiState.value.queue.channels.map { it.id }.toSet())
        assertEquals(0, channels.imports)
        player.toggleFavorite(); advanceUntilIdle()
        assertEquals("s2", player.uiState.value.channel?.id)
        assertEquals(-1, player.uiState.value.currentIndex)
        assertEquals(1, home.uiState.value.organization.counts["arabic"])
        player.nextChannel(); assertEquals("e1", player.uiState.value.channel?.id)
    }
    @Test fun refreshKeepsGroupRulesAndIncludesNewChannelsWithoutRewritingSourceFields() = runTest(dispatcher) {
        val channels = OrganizationChannelsFixture()
        val custom = ChannelFolder("custom-family", name = "Family")
        val initial = OrganizationConfig(enabled = true, folders = OrganizationDefaults.folders() + custom,
            groupOverrides = mapOf(OrganizationNames.groupKey("AR SYRIA HD") to custom.id))
        val organization = OrganizationMemory(initial)
        val playlists = OrganizationPlaylistsFixture(channels)
        val home = HomeViewModel(OrganizationSettingsFixture(), playlists, dispatcher, FakeFavoriteRepository(), organization)
        advanceUntilIdle(); home.selectGroup(FolderFilter.of(custom.id)); advanceUntilIdle()
        assertEquals(2, home.uiState.value.visibleChannels.size)
        channels.rows = channels.rows + OrganizationChannelsFixture.channel("s3", "AR SYRIA HD")
        home.refresh(); advanceUntilIdle()
        assertEquals(setOf("s1", "s2", "s3"), home.uiState.value.visibleChannels.map { it.id }.toSet())
        assertTrue(home.uiState.value.visibleChannels.all { it.group!!.startsWith("AR SYRIA") })
        assertEquals(initial, organization.value.value)
        assertEquals(0, organization.writes)
    }
    @Test fun disablingFoldersClearsOldFolderFiltersAndPreservesTheActiveChannel() = runTest(dispatcher) {
        val channels = OrganizationChannelsFixture(); val organization = OrganizationMemory()
        val preview = ChannelOrganizer.project(channels.rows, organization.value.value)
        val sourceFolder = preview.assignments.getValue("s2").folderId
        val args = mapOf("group" to FolderFilter.of(sourceFolder), "channelId" to "s2")
        val player = PlayerViewModel(SavedStateHandle(args), channels, dispatcher, FakeFavoriteRepository(), organization)
        val epg = EpgViewModel(SavedStateHandle(args), channels, dispatcher, FakeFavoriteRepository(), organization)
        advanceUntilIdle(); assertEquals(2, player.uiState.value.queue.size)
        organization.value.value = organization.value.value.copy(enabled = false)
        advanceUntilIdle()
        assertEquals("s2", player.uiState.value.channel?.id)
        assertNull(player.uiState.value.selectedGroup)
        assertEquals(channels.rows.size, player.uiState.value.queue.size)
        assertEquals("s2", epg.uiState.value.channel?.id)
        assertNull(epg.uiState.value.selectedGroup)
    }
}