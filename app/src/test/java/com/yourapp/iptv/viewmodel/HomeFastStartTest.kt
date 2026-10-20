package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.PlaylistRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFastStartTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val source = Settings(AppSettings(m3uUrl = "https://unit.test/a"))
    private val cached = Channel("stable", "Alpha cached", streamUrl = "https://unit.test/1", group = "News")
    private val fresh = cached.copy(name = "Alpha refreshed")

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owner.clear(); Dispatchers.resetMain() }

    @Test fun cachedChannelsAreUsableWhileNetworkIsStillPending() = runTest(dispatcher) {
        val repository = Lists(listOf(cached))
        val model = model(repository)
        runCurrent()
        assertEquals(listOf(cached.id), model.uiState.value.visibleChannels.map { it.id })
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.isRefreshing)
        assertEquals(1, repository.calls)
        model.updateQuery("Alpha"); model.selectChannel(cached.id)
        runCurrent()
        repository.pending.complete(Playlist("fresh", listOf(fresh)))
        runCurrent()
        assertEquals("Alpha refreshed", model.uiState.value.visibleChannels.single().name)
        assertEquals(cached.id, model.uiState.value.selectedChannelId)
        assertTrue(model.uiState.value.visibleChannels.single().isFavorite)
        assertEquals("Alpha", model.uiState.value.query)
        assertFalse(model.uiState.value.isRefreshing)
    }

    @Test fun offlineRefreshKeepsSnapshotAndOrganizationUpdatesKeepTheError() = runTest(dispatcher) {
        val repository = Lists(listOf(cached))
        val organization = OrganizationMemory()
        val model = HomeViewModel(source, repository, dispatcher, FakeFavoriteRepository(), organization)
        owner.put("home", model)
        runCurrent()
        repository.pending.completeExceptionally(PlaylistLoadException("offline"))
        runCurrent()
        assertEquals(cached.id, model.uiState.value.visibleChannels.single().id)
        assertEquals("offline", model.uiState.value.errorMessage)
        assertFalse(model.uiState.value.isRefreshing)
        organization.value.value = organization.value.value.copy(enabled = false)
        runCurrent()
        assertEquals("offline", model.uiState.value.errorMessage)
        assertEquals(cached.id, model.uiState.value.visibleChannels.single().id)
        repository.pending = CompletableDeferred()
        model.refresh(); runCurrent()
        repository.pending.complete(Playlist("recovered", listOf(fresh))); runCurrent()
        assertEquals(fresh.name, model.uiState.value.visibleChannels.single().name)
        assertNull(model.uiState.value.errorMessage)
    }

    @Test fun repeatedRefreshDoesNotDuplicateAnActiveRequest() = runTest(dispatcher) {
        val repository = Lists(listOf(cached))
        val model = model(repository)
        runCurrent()
        repeat(4) { model.refresh() }
        runCurrent()
        assertEquals(1, repository.calls)
        repository.pending.complete(Playlist("fresh", listOf(fresh)))
        runCurrent()
        repository.pending = CompletableDeferred()
        model.refresh(); model.refresh(); runCurrent()
        assertEquals(2, repository.calls)
        assertEquals(fresh.name, model.uiState.value.channels.single().name)
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.isRefreshing)
    }

    @Test fun brokenCacheStillAllowsNetworkLoad() = runTest(dispatcher) {
        val repository = Lists(emptyList()).apply { cacheFails = true }
        val model = model(repository)
        runCurrent()
        assertTrue(model.uiState.value.isLoading)
        repository.pending.complete(Playlist("fresh", listOf(fresh)))
        runCurrent()
        assertEquals(fresh.id, model.uiState.value.visibleChannels.single().id)
        assertNull(model.uiState.value.errorMessage)
    }

    @Test fun lateOldSourceResponseCannotReplaceTheNewSource() = runTest(dispatcher) {
        val old = CompletableDeferred<Playlist>()
        val next = CompletableDeferred<Playlist>()
        val repository = object : PlaylistRepository {
            override suspend fun loadPlaylist(url: String): Playlist =
                if (url.endsWith("/a")) withContext(NonCancellable) { old.await() } else next.await()
            override suspend fun loadXtream(serverUrl: String, username: String, password: String) = error("unused")
            override suspend fun cachedPlaylist(source: AppSettings) =
                if (source.m3uUrl.endsWith("/a")) Playlist("old", listOf(cached)) else null
        }
        val model = model(repository)
        runCurrent()
        assertEquals(cached.id, model.uiState.value.visibleChannels.single().id)
        source.values.value = AppSettings(m3uUrl = "https://unit.test/b")
        runCurrent()
        assertTrue(model.uiState.value.channels.isEmpty())
        val newChannel = fresh.copy(id = "new-account")
        next.complete(Playlist("new", listOf(newChannel))); runCurrent()
        old.complete(Playlist("late", listOf(cached))); runCurrent()
        assertEquals("new-account", model.uiState.value.visibleChannels.single().id)
    }

    @Test fun changingOnlyEpgDoesNotRestartPlaylistLoading() = runTest(dispatcher) {
        val repository = Lists(listOf(cached))
        model(repository); runCurrent()
        source.values.value = source.values.value.copy(epgUrl = "https://unit.test/epg.xml")
        runCurrent()
        assertEquals(1, repository.calls)
    }

    private fun model(repository: PlaylistRepository): HomeViewModel = HomeViewModel(
        source, repository, dispatcher, FakeFavoriteRepository(setOf(cached.id))
    ).also { owner.put("home", it) }

    private class Settings(initial: AppSettings) : SettingsRepository {
        val values = MutableStateFlow(initial)
        override fun observeSettings() = values
        override suspend fun saveSettings(settings: AppSettings) { values.value = settings }
    }

    private class Lists(private val rows: List<Channel>) : PlaylistRepository {
        var pending = CompletableDeferred<Playlist>()
        var calls = 0
        var cacheFails = false
        override suspend fun cachedPlaylist(source: AppSettings): Playlist? {
            if (cacheFails) error("Unreadable cache")
            return rows.takeIf { it.isNotEmpty() }?.let { Playlist("cache", it) }
        }
        override suspend fun loadPlaylist(url: String): Playlist { calls++; return pending.await() }
        override suspend fun loadXtream(serverUrl: String, username: String, password: String) = loadPlaylist(serverUrl)
    }
}