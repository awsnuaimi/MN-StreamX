package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VodInstantLoadViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val accounts = object : SettingsRepository {
        val values = MutableStateFlow(account("one"))
        override fun observeSettings() = values
        override suspend fun saveSettings(settings: AppSettings) { values.value = settings }
    }
    private val pending = CompletableDeferred<Unit>()
    private var forced = 0
    private var scoped = false

    private val catalog = object : VodRepository {
        override fun supports(settings: AppSettings) = true
        override suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog = listing("network")
        override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> =
            listOf(VodItem("new-$categoryId", "New", categoryId))
        override fun catalogUpdates(settings: AppSettings, kind: VodKind,
            force: Boolean): Flow<VodLoad<VodCatalog>> = flow {
            if (force) forced++ else {
                val old = if (scoped) scopedListing() else listing("old-${settings.xtreamUsername}")
                emit(VodLoad(old, refreshing = true))
            }
            if (settings.xtreamUsername == "one") pending.await()
            emit(VodLoad(if (scoped) scopedListing() else listing("new-${settings.xtreamUsername}")))
        }
        override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem) = VodDetails(item)
        override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String) = "unused"
    }

    private fun model() = VodViewModel(accounts, catalog).also { store.put("vod", it) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun localResultsAppearWhileNetworkIsPendingAndSearchSurvivesUpdate() = runTest(dispatcher) {
        val model = model()
        model.load(VodKind.MOVIE); runCurrent()
        assertFalse(model.uiState.value.loading)
        assertTrue(model.uiState.value.refreshing)
        assertEquals("old-one", model.uiState.value.visible.single().id)
        model.search("New"); runCurrent()
        pending.complete(Unit); runCurrent()
        assertEquals("New", model.uiState.value.visible.single().name)
        assertEquals("New", model.uiState.value.query)
        assertFalse(model.uiState.value.refreshing)
    }

    @Test fun switchingAccountsCancelsOldCatalogPublication() = runTest(dispatcher) {
        val model = model(); model.load(VodKind.SERIES); runCurrent()
        assertEquals("old-one", model.uiState.value.visible.single().id)
        accounts.values.value = account("two"); runCurrent()
        assertEquals("new-two", model.uiState.value.visible.single().id)
        pending.complete(Unit); runCurrent()
        assertEquals("new-two", model.uiState.value.visible.single().id)
        assertFalse(model.uiState.value.refreshing)
    }

    @Test fun manualRefreshWaitsForNetworkAndDoesNotPublishStaleCache() = runTest(dispatcher) {
        pending.complete(Unit)
        val model = model(); model.load(VodKind.MOVIE, refresh = true); runCurrent()
        assertEquals(1, forced)
        assertEquals("new-one", model.uiState.value.visible.single().id)
        assertFalse(model.uiState.value.refreshing)
    }

    @Test fun categorySelectionIsNotResetWhenCategoryMetadataRefreshes() = runTest(dispatcher) {
        scoped = true
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        model.filter("b"); runCurrent()
        assertEquals("b", model.uiState.value.categoryId)
        pending.complete(Unit); runCurrent()
        assertEquals("b", model.uiState.value.categoryId)
        assertEquals("new-b", model.uiState.value.catalog.items.single().id)
    }

    private fun listing(id: String) = VodCatalog(emptyList(), listOf(VodItem(id, "New", null)))
    private fun scopedListing() = VodCatalog(listOf(VodCategory("a", "A"), VodCategory("b", "B")),
        emptyList(), categoryScoped = true)

    companion object {
        private fun account(username: String) = AppSettings(source = PlaylistSource.XTREAM,
            xtreamServer = "https://synthetic.invalid", xtreamUsername = username, xtreamPassword = "synthetic")
    }
}