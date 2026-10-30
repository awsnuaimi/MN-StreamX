package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeBrowseSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val source = AppSettings(m3uUrl = "https://unit.test/list-a")
    private val settings = Settings(source)
    private val rows = (0..50).map { Channel("id-$it", "Channel $it", streamUrl = "https://unit.test/$it",
        group = if (it < 45) "PL News" else "DE Sport") }
    private val config = OrganizationConfig(sourceKey = OrganizationNames.sourceKey(source), enabled = true)
    private val catalog = ChannelOrganizer.project(rows, config)
    private val sessions = Sessions()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }

    @Test fun aSavedFolderFocusAndPositionRestoreFromTheReadyCatalog() = runTest(dispatcher) {
        val group = FolderFilter.of(catalog.assignments.getValue("id-40").folderId)
        val saved = HomeBrowseSession(group, "polish", focusedChannelId = "id-40",
            position = HomeGridPosition("id-36", 13, 3))
        sessions.result.complete(saved)
        val model = model(); model.configureHomeSession(true); runCurrent()
        val state = model.uiState.value
        assertEquals(group, state.selectedGroup); assertEquals("id-40", state.selectedChannelId)
        assertEquals(saved.position, state.gridRestore?.position)
        assertTrue(state.sessionReady); assertFalse(state.isLoading)
        assertEquals(45, state.visibleChannels.size)
        val token = state.gridRestore!!.token
        model.onGridRestoreHandled(token - 1); assertNotNull(model.uiState.value.gridRestore)
        model.onGridRestoreHandled(token); assertNull(model.uiState.value.gridRestore)
    }

    @Test fun aSlowSessionReadDoesNotBlockCatalogDisplayOrUndoAnExplicitSelection() = runTest(dispatcher) {
        val model = model(); model.configureHomeSession(true); runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertEquals(51, model.uiState.value.visibleChannels.size)
        val chosen = FolderFilter.of(catalog.assignments.getValue("id-50").folderId)
        model.selectGroup(chosen); runCurrent()
        sessions.result.complete(HomeBrowseSession(FolderFilter.of("polish"), focusedChannelId = "id-40")); runCurrent()
        assertEquals(chosen, model.uiState.value.selectedGroup)
        assertNull(model.uiState.value.gridRestore)
        assertEquals(chosen, sessions.writes.last().second.group)
    }

    @Test fun disabledRestorationLeavesTheNormalHomeAndDoesNotSaveFocus() = runTest(dispatcher) {
        val model = model(); model.configureHomeSession(false); runCurrent()
        assertEquals(0, sessions.reads); assertNull(model.uiState.value.selectedGroup)
        model.onBrowsePosition(model.uiState.value.browseScope, null, false, "id-40", HomeGridPosition("id-36", 9, 3))
        runCurrent()
        assertTrue(sessions.writes.isEmpty())
    }

    @Test fun focusMovesRecordNavigationWithoutRebuildingOrPublishingTheChannelList() = runTest(dispatcher) {
        sessions.result.complete(null)
        val model = model(); model.configureHomeSession(true); runCurrent()
        val original = model.uiState.value
        repeat(45) { model.onBrowsePosition(original.browseScope, null, false, "id-$it", HomeGridPosition("id-36", 9, 3)) }
        runCurrent()
        assertSame(original, model.uiState.value)
        assertSame(original.visibleChannels, model.uiState.value.visibleChannels)
        assertEquals("id-44", sessions.writes.last().second.focusedChannelId)
        assertEquals(PlaylistCacheKey.of(source), sessions.writes.last().first)
        model.onBrowsePosition("old-source", null, false, "id-0", HomeGridPosition())
        assertEquals("id-44", sessions.writes.last().second.focusedChannelId)
    }

    @Test fun searchResultsDoNotOverwriteTheNormalBrowsingSession() = runTest(dispatcher) {
        sessions.result.complete(null)
        val model = model(); model.configureHomeSession(true); runCurrent()
        model.onBrowsePosition(model.uiState.value.browseScope, null, false, "id-40", HomeGridPosition("id-36", 9, 3))
        model.updateQuery("Channel 0"); runCurrent()
        model.onBrowsePosition(model.uiState.value.browseScope, null, false, "id-0", HomeGridPosition("id-0", 0, 3))
        assertEquals("id-40", sessions.writes.last().second.focusedChannelId)
    }

    @Test fun aFailingOptionalSessionReadCannotPreventOpening() = runTest(dispatcher) {
        sessions.result.completeExceptionally(IllegalStateException("Unreadable optional session"))
        val model = model(); model.configureHomeSession(true); runCurrent()
        assertEquals(51, model.uiState.value.visibleChannels.size)
        assertTrue(model.uiState.value.sessionReady); assertNull(model.uiState.value.errorMessage)
    }

    @Test fun savedFavoritesWaitForCurrentFavoriteIdsWithoutBlockingTheCatalog() = runTest(dispatcher) {
        val ids = CompletableDeferred<Set<String>>()
        val favorites = object : FavoriteRepository {
            override fun observeFavoriteIds(sourceKey: String?): Flow<Set<String>> = flow { emit(ids.await()) }
            override suspend fun toggleFavorite(channelId: String, sourceKey: String?) = Unit
            override suspend fun setFavorite(channelId: String, selected: Boolean, sourceKey: String?) = Unit
        }
        sessions.result.complete(HomeBrowseSession(favoritesOnly = true, focusedChannelId = "id-40"))
        val model = model(favorites); model.configureHomeSession(true); runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertEquals(51, model.uiState.value.visibleChannels.size)
        ids.complete(setOf("id-40")); runCurrent()
        assertTrue(model.uiState.value.favoritesOnly)
        assertEquals("id-40", model.uiState.value.visibleChannels.single().id)
    }

    @Test fun unavailableFavoritesFallBackToBrowsableChannels() = runTest(dispatcher) {
        val favorites = object : FavoriteRepository {
            override fun observeFavoriteIds(sourceKey: String?): Flow<Set<String>> = flow { error("Read unavailable") }
            override suspend fun toggleFavorite(channelId: String, sourceKey: String?) = Unit
            override suspend fun setFavorite(channelId: String, selected: Boolean, sourceKey: String?) = Unit
        }
        sessions.result.complete(HomeBrowseSession(favoritesOnly = true, focusedChannelId = "id-40"))
        val model = model(favorites); model.configureHomeSession(true); runCurrent()
        assertTrue(model.uiState.value.sessionReady)
        assertFalse(model.uiState.value.favoritesOnly)
        assertEquals("id-40", model.uiState.value.selectedChannelId)
    }

    @Test fun aRemoteInteractionCancelsAPendingFocusRequest() = runTest(dispatcher) {
        sessions.result.complete(HomeBrowseSession(focusedChannelId = "id-40"))
        val model = model(); model.configureHomeSession(true); runCurrent()
        assertNotNull(model.uiState.value.gridRestore)
        model.onBrowseInteraction(); runCurrent()
        assertNull(model.uiState.value.gridRestore)
        assertNull(model.uiState.value.selectedChannelId)
    }

    @Test fun sessionStateCannotLeakAcrossSourcesWithOverlappingChannelIds() = runTest(dispatcher) {
        sessions.result.complete(HomeBrowseSession("PL News", focusedChannelId = "id-40"))
        val model = model(); model.configureHomeSession(true); runCurrent()
        assertNotNull(model.uiState.value.selectedGroup)
        sessions.result = CompletableDeferred<HomeBrowseSession?>().apply { complete(null) }
        settings.value.value = source.copy(m3uUrl = "https://unit.test/list-b")
        runCurrent()
        assertNull(model.uiState.value.selectedGroup)
        assertNull(model.uiState.value.selectedChannelId)
        assertEquals(PlaylistCacheKey.of(settings.value.value), model.uiState.value.browseScope)
    }

    @Test fun switchingCategoriesRestoresTheirOwnChannelsAndScrollPositionsWithoutStealingFocus() = runTest(dispatcher) {
        sessions.result.complete(null)
        val model = model(); model.configureHomeSession(true); runCurrent()
        val a = FolderFilter.of(catalog.assignments.getValue("id-40").folderId)
        val b = FolderFilter.of(catalog.assignments.getValue("id-50").folderId)
        val positionA = HomeGridPosition("id-36", 12, 1)
        val positionB = HomeGridPosition("id-45", 7, 1)
        model.selectGroup(a); runCurrent()
        model.onBrowsePosition(model.uiState.value.browseScope, a, false, "id-40", positionA)
        model.selectGroup(b); runCurrent()
        model.onBrowsePosition(model.uiState.value.browseScope, b, false, "id-50", positionB)
        model.selectGroup(a); runCurrent()
        assertEquals("id-40", model.uiState.value.selectedChannelId)
        assertEquals(positionA, model.uiState.value.gridRestore?.position)
        assertEquals(false, model.uiState.value.gridRestore?.takeFocus)
        model.onBrowseInteraction(); runCurrent()
        assertEquals("id-40", model.uiState.value.selectedChannelId)
        assertNotNull(model.uiState.value.gridRestore)
        model.selectGroup(b); runCurrent()
        assertEquals("id-50", model.uiState.value.selectedChannelId)
        assertEquals(positionB, model.uiState.value.gridRestore?.position)
        assertEquals(2, sessions.writes.last().second.bookmarks.size)
    }

    @Test fun clearingSearchRestoresTheUnfilteredCategoryBookmark() = runTest(dispatcher) {
        sessions.result.complete(null)
        val model = model(); model.configureHomeSession(true); runCurrent()
        val position = HomeGridPosition("id-36", 11, 1)
        model.onBrowsePosition(model.uiState.value.browseScope, null, false, "id-40", position)
        model.updateQuery("Channel 0"); runCurrent()
        model.updateQuery(""); runCurrent()
        assertEquals("id-40", model.uiState.value.selectedChannelId)
        assertEquals(position, model.uiState.value.gridRestore?.position)
    }

    @Test fun perCategoryHistoryLoadsFromStorageButCannotCrossSources() = runTest(dispatcher) {
        val a = FolderFilter.of(catalog.assignments.getValue("id-40").folderId)
        val b = FolderFilter.of(catalog.assignments.getValue("id-50").folderId)
        sessions.result.complete(HomeBrowseSession(a, focusedChannelId = "id-40",
            bookmarks = listOf(HomeBrowseBookmark(b, focusedChannelId = "id-50"))))
        val model = model(); model.configureHomeSession(true); runCurrent()
        model.selectGroup(b); runCurrent()
        assertEquals("id-50", model.uiState.value.selectedChannelId)
        sessions.result = CompletableDeferred<HomeBrowseSession?>().apply { complete(null) }
        settings.value.value = source.copy(m3uUrl = "https://unit.test/list-b"); runCurrent()
        model.selectGroup(b); runCurrent()
        assertNull(model.uiState.value.selectedChannelId)
    }

    private fun model(favorites: FavoriteRepository = FakeFavoriteRepository()) = HomeViewModel(settings,
        object : PlaylistRepository {
            override suspend fun loadPlaylist(url: String): Playlist = error("Fresh catalog must not use the network")
            override suspend fun loadXtream(serverUrl: String, username: String, password: String) = loadPlaylist(serverUrl)
        }, dispatcher, favorites, OrganizationMemory(config), object : HomeStartupRepository {
            override suspend fun restore(source: AppSettings): HomeStartupSnapshot {
                val current = config.copy(sourceKey = OrganizationNames.sourceKey(source))
                return HomeStartupSnapshot(current, ChannelOrganizer.project(rows, current), System.currentTimeMillis())
            }
            override suspend fun save(source: AppSettings, config: OrganizationConfig, catalog: OrganizedChannels, refreshedAt: Long) = Unit
        }, sessions).also { owner.put("home", it) }

    private class Settings(initial: AppSettings) : SettingsRepository {
        val value = MutableStateFlow(initial)
        override fun observeSettings() = value
        override suspend fun saveSettings(settings: AppSettings) { value.value = settings }
    }
    private class Sessions : HomeSessionRepository {
        var result = CompletableDeferred<HomeBrowseSession?>()
        var reads = 0
        val writes = mutableListOf<Pair<String, HomeBrowseSession>>()
        override suspend fun read(sourceKey: String): HomeBrowseSession? { reads++; return result.await() }
        override fun remember(sourceKey: String, value: HomeBrowseSession) { writes += sourceKey to value }
        override fun flush() = Unit
    }
}