package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomePersistentStartupTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val source = AppSettings(m3uUrl = "https://unit.test/list")
    private val settings = Settings(source)
    private val config = OrganizationConfig(sourceKey = OrganizationNames.sourceKey(source), enabled = true)
    private val organization = OrganizationMemory(config)
    private val rows = listOf(Channel("a", "TVP 1", streamUrl = "https://unit.test/a", group = "PL News"),
        Channel("b", "ZDF", streamUrl = "https://unit.test/b", group = "DE News"))
    private val catalog = ChannelOrganizer.project(rows, config)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { owner.clear(); Dispatchers.resetMain() }

    @Test fun freshCatalogOpensWithoutRawChannelReadsNetworkOrRewritingTheCatalog() = runTest(dispatcher) {
        val lists = Lists()
        val startup = Snapshot(HomeStartupSnapshot(config, catalog, System.currentTimeMillis()))
        val model = model(lists, startup)
        runCurrent()
        assertEquals(catalog.channels.map { it.id }, model.uiState.value.visibleChannels.map { it.id })
        assertFalse(model.uiState.value.isLoading)
        assertFalse(model.uiState.value.isRefreshing)
        assertTrue(model.uiState.value.channels.first { it.id == "a" }.isFavorite)
        model.onHomeReady(); runCurrent()
        assertEquals(0, lists.networkCalls)
        assertEquals(0, lists.rawCacheReads)
        assertEquals(0, startup.saves)
    }

    @Test fun staleCatalogIsUsableBeforeTheUiRequestsItsDeferredRefresh() = runTest(dispatcher) {
        val lists = Lists()
        val model = model(lists, Snapshot(HomeStartupSnapshot(config, catalog, 0)))
        runCurrent()
        assertEquals(2, model.uiState.value.visibleChannels.size)
        assertEquals(0, lists.networkCalls)
        model.onHomeReady(); model.onHomeReady(); runCurrent()
        assertEquals(1, lists.networkCalls)
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.isRefreshing)
        lists.result.completeExceptionally(PlaylistLoadException("offline")); runCurrent()
        assertEquals(2, model.uiState.value.visibleChannels.size)
        assertEquals("offline", model.uiState.value.errorMessage)
    }

    @Test fun manualRefreshAlwaysWorksEvenWhenTheSavedCatalogIsFresh() = runTest(dispatcher) {
        val lists = Lists()
        val startup = Snapshot(HomeStartupSnapshot(config, catalog, System.currentTimeMillis()))
        val model = model(lists, startup)
        runCurrent()
        model.refresh(); model.refresh(); model.onHomeReady(); runCurrent()
        assertEquals(1, lists.networkCalls)
        lists.result.complete(Playlist("updated", rows)); runCurrent()
        assertTrue(startup.saves > 0)
        assertTrue(startup.lastRefreshedAt > 0)
    }

    @Test fun unavailableCatalogFallsBackToTheExistingImportPath() = runTest(dispatcher) {
        val lists = Lists().apply { rawRows = rows }
        val model = model(lists, Snapshot(null).apply { failRead = true })
        runCurrent()
        assertEquals(1, lists.rawCacheReads)
        assertEquals(1, lists.networkCalls)
        assertEquals(2, model.uiState.value.visibleChannels.size)
    }

    @Test fun searchAndClearRestoreAllFolderCounts() = runTest(dispatcher) {
        val model = model(Lists(), Snapshot(HomeStartupSnapshot(config, catalog, System.currentTimeMillis())))
        runCurrent()
        model.updateQuery("TVP"); runCurrent()
        assertEquals(0, model.uiState.value.organization.counts["german"] ?: 0)
        model.clearFilters(); runCurrent()
        assertEquals(catalog.tree.counts, model.uiState.value.organization.counts)
        val folder = catalog.assignments.getValue("b").folderId
        model.selectGroup(FolderFilter.of(folder)); runCurrent()
        assertEquals("b", model.uiState.value.visibleChannels.single().id)
    }

    @Test fun organizationChangesRebuildAndPersistWithoutRefreshingTheNetwork() = runTest(dispatcher) {
        val lists = Lists()
        val startup = Snapshot(HomeStartupSnapshot(config, catalog, 12345))
        val model = model(lists, startup)
        runCurrent()
        organization.value.value = config.copy(channelOverrides = mapOf("a" to "german"), revision = 1)
        runCurrent()
        assertEquals(0, lists.networkCalls)
        assertTrue(startup.saves > 0)
        assertEquals(12345L, startup.lastRefreshedAt)
        assertTrue("german" in model.uiState.value.channels.first { it.id == "a" }.folderIds)
    }

    @Test fun anEmptyImportEndsTheInitialLoadingState() = runTest(dispatcher) {
        val lists = Lists()
        val model = model(lists, Snapshot(null))
        runCurrent()
        lists.result.complete(Playlist("empty", emptyList())); runCurrent()
        assertTrue(model.uiState.value.channels.isEmpty())
        assertFalse(model.uiState.value.isLoading)
    }

    private fun model(lists: Lists, startup: Snapshot) = HomeViewModel(settings, lists, dispatcher,
        FakeFavoriteRepository(setOf("a")), organization, startup).also { owner.put("home", it) }

    private class Settings(initial: AppSettings) : SettingsRepository {
        val value = MutableStateFlow(initial)
        override fun observeSettings() = value
        override suspend fun saveSettings(settings: AppSettings) { value.value = settings }
    }
    private class Snapshot(private val value: HomeStartupSnapshot?) : HomeStartupRepository {
        var saves = 0
        var lastRefreshedAt = 0L
        var failRead = false
        override suspend fun restore(source: AppSettings): HomeStartupSnapshot? {
            if (failRead) error("Broken cache")
            return value
        }
        override suspend fun save(source: AppSettings, config: OrganizationConfig, catalog: OrganizedChannels, refreshedAt: Long) {
            saves++; lastRefreshedAt = refreshedAt
        }
    }
    private class Lists : PlaylistRepository {
        var networkCalls = 0
        var rawCacheReads = 0
        var rawRows: List<Channel> = emptyList()
        val result = CompletableDeferred<Playlist>()
        override suspend fun cachedPlaylist(source: AppSettings): Playlist? {
            rawCacheReads++
            return rawRows.takeIf { it.isNotEmpty() }?.let { Playlist("raw", it) }
        }
        override suspend fun loadPlaylist(url: String): Playlist { networkCalls++; return result.await() }
        override suspend fun loadXtream(serverUrl: String, username: String, password: String) = loadPlaylist(serverUrl)
    }
}