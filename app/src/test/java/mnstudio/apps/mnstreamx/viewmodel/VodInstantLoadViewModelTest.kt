package mnstudio.apps.mnstreamx.viewmodel

import androidx.lifecycle.ViewModelStore
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private var refreshedCatalog: VodCatalog? = null
    private var detailsBlock: suspend (VodItem) -> VodDetails = { VodDetails(it) }
    private val categoryCalls = mutableListOf<Pair<String, String>>()

    private val catalog = object : VodRepository {
        override fun supports(settings: AppSettings) = true
        override suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog = listing("network")
        override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> {
            categoryCalls += settings.xtreamUsername to categoryId
            return listOf(VodItem("new-$categoryId", "New", categoryId))
        }
        override fun catalogUpdates(settings: AppSettings, kind: VodKind,
            force: Boolean): Flow<VodLoad<VodCatalog>> = flow {
            if (force) forced++ else {
                val old = if (scoped) scopedListing() else listing("old-${settings.xtreamUsername}")
                emit(VodLoad(old, refreshing = true))
            }
            if (settings.xtreamUsername == "one") pending.await()
            emit(VodLoad(refreshedCatalog ?: if (scoped) scopedListing() else listing("new-${settings.xtreamUsername}")))
        }
        override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem) = detailsBlock(item)
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


    @Test fun backgroundRefreshClosesSelectionWhenTheSelectedTitleWasRemoved() = runTest(dispatcher) {
        detailsBlock = { item ->
            withContext(NonCancellable) { delay(100) }
            VodDetails(item)
        }
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        val selected = model.uiState.value.visible.single()
        model.open(selected); runCurrent()
        assertEquals(selected.id, model.uiState.value.selected?.id)
        refreshedCatalog = listing("replacement")
        pending.complete(Unit); runCurrent()
        assertEquals("replacement", model.uiState.value.visible.single().id)
        assertNull(model.uiState.value.selected)
        assertNull(model.uiState.value.details)
        assertFalse(model.uiState.value.detailLoading)
        advanceTimeBy(101); runCurrent()
        assertNull(model.uiState.value.selected)
        assertNull(model.uiState.value.details)
    }

    @Test fun backgroundRefreshKeepsSelectionAndUpdatesItsCatalogMetadataByStableId() = runTest(dispatcher) {
        detailsBlock = { item ->
            withContext(NonCancellable) { delay(100) }
            VodDetails(item)
        }
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        val original = model.uiState.value.visible.single()
        model.open(original); runCurrent()
        val updated = original.copy(name = "Updated title", poster = "https://synthetic.invalid/new.jpg")
        refreshedCatalog = VodCatalog(emptyList(), listOf(updated))
        pending.complete(Unit); runCurrent()
        assertEquals(original.id, model.uiState.value.selected?.id)
        assertEquals("Updated title", model.uiState.value.selected?.name)
        advanceTimeBy(101); runCurrent()
        assertEquals("Updated title", model.uiState.value.details?.item?.name)
        assertEquals(updated.poster, model.uiState.value.details?.item?.poster)
    }

    @Test fun neighbourCategoryIsWarmedOnlyAfterIdleTime() = runTest(dispatcher) {
        scoped = true
        val model = model(); model.load(VodKind.SERIES); runCurrent()
        assertEquals(listOf("one" to "a"), categoryCalls)
        advanceTimeBy(VodCategoryWarmup.IDLE_DELAY_MS - 1); runCurrent()
        assertEquals(listOf("one" to "a"), categoryCalls)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("one" to "a", "one" to "b"), categoryCalls)
    }

    @Test fun switchingProfileCancelsPreviousCategoryWarmup() = runTest(dispatcher) {
        scoped = true
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        accounts.values.value = account("two"); runCurrent()
        advanceTimeBy(VodCategoryWarmup.IDLE_DELAY_MS); runCurrent()
        assertFalse(categoryCalls.contains("one" to "b"))
        assertTrue(categoryCalls.contains("two" to "b"))
    }

    private fun listing(id: String) = VodCatalog(emptyList(), listOf(VodItem(id, "New", null)))
    private fun scopedListing() = VodCatalog(listOf(VodCategory("a", "A"), VodCategory("b", "B")),
        emptyList(), categoryScoped = true)

    companion object {
        private fun account(username: String) = AppSettings(source = PlaylistSource.XTREAM,
            xtreamServer = "https://synthetic.invalid", xtreamUsername = username, xtreamPassword = "synthetic")
    }
}