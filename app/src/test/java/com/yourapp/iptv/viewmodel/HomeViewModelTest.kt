package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.Playlist
import com.yourapp.iptv.domain.model.PlaylistLoadException
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
        val model = HomeViewModel(settings(""), playlists, dispatcher)
        advanceUntilIdle()
        assertTrue(model.uiState.value.needsSource)
        assertEquals(0, playlists.calls)
    }

    @Test fun sourceLoadsAndRefreshFailurePreservesDisplayedChannels() = runTest(dispatcher) {
        val playlists = FakePlaylists()
        val model = HomeViewModel(settings("https://unit.test/list.m3u"), playlists, dispatcher)
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
        val model = HomeViewModel(settings("https://unit.test/list.m3u"), FakePlaylists(), dispatcher)
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

    private fun settings(url: String) = object : SettingsRepository {
        override fun observeSettings() = flowOf(AppSettings(m3uUrl = url))
        override suspend fun saveSettings(settings: AppSettings) = Unit
    }

    private class FakePlaylists : PlaylistRepository {
        var calls = 0
        var fail = false
        override suspend fun loadPlaylist(url: String): Playlist {
            calls++
            delay(20)
            if (fail) throw PlaylistLoadException("Synthetic failure")
            return Playlist("Test", listOf(
                Channel("a", "Alpha", streamUrl = "https://unit.test/a.ts", group = "News"),
                Channel("b", "Beta", streamUrl = "https://unit.test/b.ts", group = "Sports")
            ))
        }
    }
}