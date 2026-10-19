package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.PlaylistRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val channels = listOf(
        Channel("a", "Alpha News", streamUrl = "https://unit.test/a", group = "News"),
        Channel("b", "Beta News", streamUrl = "https://unit.test/b", group = "News"),
        Channel("c", "Gamma Sport", streamUrl = "https://unit.test/c", group = "Sport"))
    private val stored = StoredChannels()
    private val settings = object : SettingsRepository {
        override fun observeSettings() = flowOf(AppSettings(m3uUrl = "https://unit.test/list.m3u"))
        override suspend fun saveSettings(settings: AppSettings) = Unit
    }
    private val playlists = object : PlaylistRepository {
        override suspend fun loadPlaylist(url: String) = Playlist("Synthetic", channels)
        override suspend fun loadXtream(serverUrl: String, username: String, password: String) =
            Playlist("Synthetic", channels)
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun homeFavoritesIntersectSearchAndCategoryAndUpdateWithoutRefresh() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository(setOf("a", "c", "not-in-current-source"))
        val home = HomeViewModel(settings, playlists, dispatcher, favorites)
        advanceUntilIdle()
        home.toggleFavoritesFilter()
        advanceUntilIdle()
        assertEquals(listOf("a", "c"), home.uiState.value.visibleChannels.map { it.id })
        home.selectGroup("News")
        home.updateQuery(" News ")
        advanceUntilIdle()
        assertEquals(listOf("a"), home.uiState.value.visibleChannels.map { it.id })
        home.selectChannel("a")
        favorites.ids.value = setOf("b", "c")
        advanceUntilIdle()
        assertEquals(listOf("b"), home.uiState.value.visibleChannels.map { it.id })
        assertEquals("b", home.uiState.value.selectedChannelId)
        home.selectGroup(null)
        advanceUntilIdle()
        assertFalse(home.uiState.value.favoritesOnly)
        assertEquals(listOf("a", "b"), home.uiState.value.visibleChannels.map { it.id })
    }

    @Test fun homeRefreshCannotOverwriteCurrentFavoriteFlags() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository(setOf("a"))
        val home = HomeViewModel(settings, playlists, dispatcher, favorites)
        advanceUntilIdle()
        home.toggleFavoritesFilter()
        home.refresh()
        advanceUntilIdle()
        assertEquals(listOf("a"), home.uiState.value.visibleChannels.map { it.id })
        assertTrue(home.uiState.value.channels.first().isFavorite)
    }

    @Test fun playerRemovalKeepsCurrentStreamAndNextFollowsRemainingFavoriteOrder() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository(setOf("a", "b", "c"))
        val player = player("b", favorites)
        advanceUntilIdle()
        player.toggleFavorite()
        advanceUntilIdle()
        assertEquals("b", player.uiState.value.channel?.id)
        assertFalse(player.uiState.value.channel!!.isFavorite)
        assertEquals(-1, player.uiState.value.currentIndex)
        assertEquals(listOf("a", "c"), player.uiState.value.queue.channels.map { it.id })
        player.nextChannel()
        assertEquals("c", player.uiState.value.channel?.id)
        player.nextChannel()
        assertEquals("a", player.uiState.value.channel?.id)
        player.previousChannel()
        assertEquals("c", player.uiState.value.channel?.id)
        assertEquals(1, stored.loads)
    }

    @Test fun previousAfterRemovalSelectsThePreviousRemainingFavorite() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository(setOf("a", "b", "c"))
        val player = player("b", favorites)
        advanceUntilIdle()
        player.toggleFavorite()
        advanceUntilIdle()
        player.previousChannel()
        assertEquals("a", player.uiState.value.channel?.id)
    }

    @Test fun removingLastFavoriteKeepsPlaybackAndAddingItBackReattachesQueue() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository(setOf("b"))
        val player = player("b", favorites)
        advanceUntilIdle()
        val stream = player.uiState.value.channel!!.streamUrl
        player.toggleFavorite()
        advanceUntilIdle()
        assertEquals(0, player.uiState.value.queue.size)
        assertEquals(stream, player.uiState.value.channel?.streamUrl)
        assertFalse(player.uiState.value.canChangeChannel)
        assertNull(player.uiState.value.errorMessage)
        player.nextChannel()
        assertEquals("b", player.uiState.value.channel?.id)
        player.toggleFavorite()
        advanceUntilIdle()
        assertEquals(0, player.uiState.value.currentIndex)
        assertTrue(player.uiState.value.channel!!.isFavorite)
    }

    @Test fun favoriteScopeAndRemovedCurrentChannelSurvivePlayerRecreation() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository(setOf("a", "c"))
        val saved = SavedStateHandle(mapOf("channelId" to "b", "favoritesOnly" to true))
        val first = PlayerViewModel(saved, stored, dispatcher, favorites)
        advanceUntilIdle()
        assertEquals("b", first.uiState.value.channel?.id)
        first.nextChannel()
        val restored = PlayerViewModel(saved, stored, dispatcher, favorites)
        advanceUntilIdle()
        assertEquals("c", restored.uiState.value.channel?.id)
        assertTrue(restored.uiState.value.favoritesOnly)
        assertEquals(listOf("a", "c"), restored.uiState.value.queue.channels.map { it.id })
    }

    @Test fun allChannelsScopeOnlyChangesTheStarAndKeepsTheSelection() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository()
        val player = PlayerViewModel(SavedStateHandle(mapOf("channelId" to "b")), stored, dispatcher, favorites)
        advanceUntilIdle()
        player.toggleFavorite()
        player.selectChannel("c")
        advanceUntilIdle()
        assertEquals("c", player.uiState.value.channel?.id)
        assertTrue(player.uiState.value.queue.channels[1].isFavorite)
        assertEquals(3, player.uiState.value.queue.size)
    }

    @Test fun guideAndHomeReceivePlayerChangesWithoutReloadingChannels() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository(setOf("a", "b"))
        val home = HomeViewModel(settings, playlists, dispatcher, favorites)
        val guide = EpgViewModel(SavedStateHandle(mapOf("channelId" to "b", "favoritesOnly" to true)),
            stored, dispatcher, favorites)
        val player = player("b", favorites)
        advanceUntilIdle()
        guide.selectProgram("1700000000000:1700003600000")
        player.toggleFavorite()
        advanceUntilIdle()
        assertFalse(home.uiState.value.channels[1].isFavorite)
        assertEquals("a", guide.uiState.value.channel?.id)
        assertNull(guide.uiState.value.selectedProgramKey)
        assertEquals(2, stored.loads)
    }

    @Test fun guideFavoriteChangeKeepsProgramSelectionForTheSameChannel() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository()
        val guide = EpgViewModel(SavedStateHandle(mapOf("channelId" to "b")), stored, dispatcher, favorites)
        advanceUntilIdle()
        guide.selectProgram("1700000000000:1700003600000")
        guide.toggleFavorite()
        advanceUntilIdle()
        assertEquals("b", guide.uiState.value.channel?.id)
        assertEquals("1700000000000:1700003600000", guide.uiState.value.selectedProgramKey)
        assertTrue(guide.uiState.value.channel!!.isFavorite)
        assertFalse(guide.uiState.value.isFiltering)
    }

    @Test fun guideFavoriteFilterSurvivesRecreationAndEmptyListCanBeCleared() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository()
        val saved = SavedStateHandle()
        val guide = EpgViewModel(saved, stored, dispatcher, favorites)
        advanceUntilIdle()
        guide.toggleFavoritesFilter()
        advanceUntilIdle()
        assertNull(guide.uiState.value.channel)
        val restored = EpgViewModel(saved, stored, dispatcher, favorites)
        advanceUntilIdle()
        assertTrue(restored.uiState.value.favoritesOnly)
        restored.clearFilters()
        advanceUntilIdle()
        assertEquals(3, restored.uiState.value.queue.size)
        assertFalse(restored.uiState.value.favoritesOnly)
    }

    @Test fun savingFailureDoesNotStopPlaybackOrExposePrivateDetails() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository(setOf("b")).apply { failWrite = true }
        val player = player("b", favorites)
        advanceUntilIdle()
        player.toggleFavorite()
        advanceUntilIdle()
        assertTrue(player.uiState.value.channel!!.isFavorite)
        assertNull(player.uiState.value.errorMessage)
        assertFalse(player.favoriteState.value.isSaving)
        assertFalse(player.favoriteState.value.errorMessage!!.contains("private-write-details"))
    }

    private fun player(id: String, favorites: FakeFavoriteRepository) = PlayerViewModel(
        SavedStateHandle(mapOf("channelId" to id, "favoritesOnly" to true)), stored, dispatcher, favorites)

    private inner class StoredChannels : ChannelRepository {
        var loads = 0
        override suspend fun getChannels(): List<Channel> { loads++; delay(20); return channels }
        override suspend fun getChannel(id: String): Channel? = error("Unexpected lookup")
        override suspend fun loadFromM3u(url: String): List<Channel> = error("Unexpected import")
        override suspend fun loadFromXtream(serverUrl: String, username: String, password: String): List<Channel> =
            error("Unexpected import")
        override suspend fun search(query: String): List<Channel> = error("Unexpected search")
        override suspend fun getFavorites(): List<Channel> = error("Unexpected reload")
        override suspend fun addFavorite(channel: Channel) = error("Use favorite repository")
        override suspend fun removeFavorite(channel: Channel) = error("Use favorite repository")
    }
}