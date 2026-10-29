package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.model.PlaylistLoadException
import com.yourapp.iptv.domain.model.PlaylistSource
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun noConfiguredSourceDoesNotMakeNetworkRequest() = runTest(dispatcher) {
        val playlists = FakePlaylists()
        val model = HomeViewModel(settings(""), playlists, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertTrue(model.uiState.value.needsSource)
        assertEquals(0, playlists.calls)
    }

    @Test fun sourceLoadsAndRefreshFailurePreservesDisplayedChannels() = runTest(dispatcher) {
        val playlists = FakePlaylists()
        val model = HomeViewModel(settings("https://unit.test/list.m3u"), playlists, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.channels.size)
        playlists.fail = true
        model.refresh()
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.channels.size)
        assertEquals("Synthetic failure", model.uiState.value.errorMessage)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test fun searchAndGroupFilterCombine() = runTest(dispatcher) {
        val model = HomeViewModel(settings("https://unit.test/list.m3u"), FakePlaylists(), dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        model.updateQuery("Beta")
        advanceUntilIdle()
        assertEquals("b", model.uiState.value.visibleChannels.single().id)
        model.selectGroup("News")
        advanceUntilIdle()
        assertTrue(model.uiState.value.visibleChannels.isEmpty())
        model.clearFilters()
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.visibleChannels.size)
    }

    @Test fun selectedXtreamTakesPrecedenceOverPreviouslySavedM3u() = runTest(dispatcher) {
        val playlists = FakePlaylists()
        HomeViewModel(source(AppSettings(m3uUrl = "https://unit.test/old.m3u",
            xtreamServer = "https://unit.test:8080", xtreamUsername = "test-user",
            xtreamPassword = "test-password", source = PlaylistSource.XTREAM)), playlists, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertEquals(1, playlists.xtreamCalls)
        assertEquals(0, playlists.calls)
    }

    @Test fun incompleteXtreamDoesNotUseOldM3uOrMakeRequests() = runTest(dispatcher) {
        val playlists = FakePlaylists()
        val model = HomeViewModel(source(AppSettings(m3uUrl = "https://unit.test/old.m3u",
            xtreamServer = "https://unit.test", source = PlaylistSource.XTREAM)), playlists, dispatcher, FakeFavoriteRepository())
        advanceUntilIdle()
        assertTrue(model.uiState.value.needsSource)
        assertEquals(0, playlists.xtreamCalls)
        assertEquals(0, playlists.calls)
    }

    @Test fun sidebarSectionsAreIdempotentAndClearPreviousFilters() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository()
        val model = HomeViewModel(settings("https://unit.test/list.m3u"), FakePlaylists(), dispatcher, favorites)
        advanceUntilIdle()
        model.updateQuery("Beta"); model.selectGroup("Sports"); advanceUntilIdle()
        model.selectSection(true); advanceUntilIdle()
        assertTrue(model.uiState.value.favoritesOnly)
        assertEquals("", model.uiState.value.query)
        assertEquals(null, model.uiState.value.selectedGroup)
        model.selectSection(true); advanceUntilIdle()
        assertTrue(model.uiState.value.favoritesOnly)
        model.selectGroup("News"); advanceUntilIdle()
        model.selectAllGroups(); advanceUntilIdle()
        assertTrue(model.uiState.value.favoritesOnly)
        assertEquals(null, model.uiState.value.selectedGroup)
        model.selectSection(false); advanceUntilIdle()
        assertFalse(model.uiState.value.favoritesOnly)
        assertEquals(2, model.uiState.value.visibleChannels.size)
    }

    @Test fun browserFavoriteUsesTheVisibleChannelAndExistingSaveFeedback() = runTest(dispatcher) {
        val favorites = FakeFavoriteRepository()
        val model = HomeViewModel(settings("https://unit.test/list.m3u"), FakePlaylists(), dispatcher, favorites)
        advanceUntilIdle()
        model.updateQuery("Beta"); advanceUntilIdle()
        model.toggleFavorite("a", model.uiState.value.browseScope); advanceUntilIdle(); assertEquals(0, favorites.writes)
        model.toggleFavorite("b", "stale-render-owner"); advanceUntilIdle(); assertEquals(0, favorites.writes)
        model.toggleFavorite("b", model.uiState.value.browseScope); advanceUntilIdle()
        assertEquals(setOf("b"), favorites.ids.value); assertEquals(1, favorites.writes)
        assertTrue(model.favoriteState.value.canToggle)
    }

    @Test fun browserFavoriteChecksRawSourceRulesForNondefaultProfiles() = runTest(dispatcher) {
        val account = AppSettings(m3uUrl = "https://unit.test/list.m3u", profileId = "11111111-1111-4111-8111-111111111111")
        val raw = com.yourapp.iptv.domain.model.PlaylistCacheKey.of(account)
        val access = kotlinx.coroutines.flow.MutableStateFlow(com.yourapp.iptv.domain.parental.ParentalState(
            ready = true, configured = true, profileId = account.profileId,
            rules = com.yourapp.iptv.domain.parental.ParentalRules(groups = mapOf(raw to setOf("News")))))
        val control = object : com.yourapp.iptv.domain.parental.ParentalControl by com.yourapp.iptv.domain.parental.ParentalControl.Disabled {
            override val state = access
        }
        val favorites = FakeFavoriteRepository()
        var destination: String? = null
        val repository = object : com.yourapp.iptv.domain.repository.FavoriteRepository by favorites {
            override suspend fun toggleFavorite(channelId: String, sourceKey: String?) {
                destination = sourceKey; favorites.toggleFavorite(channelId, sourceKey)
            }
        }
        val model = HomeViewModel(source(account), FakePlaylists(), dispatcher, repository, parental = control)
        advanceUntilIdle()
        model.toggleFavorite("a", model.uiState.value.browseScope); advanceUntilIdle(); assertEquals(0, favorites.writes)
        access.value = access.value.copy(unlocked = true, epoch = 1)
        model.toggleFavorite("a", model.uiState.value.browseScope); advanceUntilIdle(); assertEquals(1, favorites.writes)
        assertEquals(com.yourapp.iptv.domain.profile.ProfileScope.of(account), destination)
        access.value = access.value.copy(profileId = "default", epoch = 2)
        model.toggleFavorite("b", model.uiState.value.browseScope); advanceUntilIdle(); assertEquals(1, favorites.writes)
    }

    private fun settings(url: String) = source(AppSettings(m3uUrl = url))

    private fun source(value: AppSettings) = object : SettingsRepository {
        override fun observeSettings() = flowOf(value)
        override suspend fun saveSettings(settings: AppSettings) = Unit
    }

    private class FakePlaylists : PlaylistRepository {
        var calls = 0
        var xtreamCalls = 0
        var fail = false
        override suspend fun loadXtream(serverUrl: String, username: String, password: String): Playlist {
            xtreamCalls++
            return result()
        }
        override suspend fun loadPlaylist(url: String): Playlist {
            calls++
            return result()
        }
        private suspend fun result(): Playlist {
            delay(20)
            if (fail) throw PlaylistLoadException("Synthetic failure")
            return Playlist("Test", listOf(
                Channel("a", "Alpha", streamUrl = "https://unit.test/a.ts", group = "News"),
                Channel("b", "Beta", streamUrl = "https://unit.test/b.ts", group = "Sports")
            ))
        }
    }
}