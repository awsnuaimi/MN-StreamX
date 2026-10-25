package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.VodRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class VodViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private class Accounts : SettingsRepository {
        val values = MutableStateFlow(account("A"))
        override fun observeSettings() = values
        override suspend fun saveSettings(settings: AppSettings) { values.value = settings }
    }
    private class Catalog : VodRepository {
        var loads = 0
        val categoryRequests = mutableListOf<String>()
        var catalogBlock: suspend (AppSettings, VodKind) -> VodCatalog = { settings, _ ->
            VodCatalog(emptyList(), listOf(VodItem("1", settings.xtreamUsername, "7")))
        }
        var categoryBlock: suspend (String) -> List<VodItem> = { listOf(VodItem(it, "Movie $it", it)) }
        var detailsBlock: suspend (VodItem) -> VodDetails = { VodDetails(it) }
        override fun supports(settings: AppSettings) = settings.source == PlaylistSource.XTREAM
        override suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog {
            loads++; return catalogBlock(settings, kind)
        }
        override suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> {
            categoryRequests += categoryId; return categoryBlock(categoryId)
        }
        override suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem) = detailsBlock(item)
        override fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String) = "unused"
    }
    private val accounts = Accounts()
    private val catalog = Catalog()
    private fun model() = VodViewModel(accounts, catalog).also { store.put("vod", it) }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    companion object {
        fun account(user: String) = AppSettings(source = PlaylistSource.XTREAM,
            xtreamServer = "https://synthetic.invalid", xtreamUsername = user, xtreamPassword = "synthetic")
    }

    @Test fun changingSubscriptionReloadsMoviesWithoutReopeningAndClearsPreviousFilters() = runTest(dispatcher) {
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        assertEquals("A", model.uiState.value.visible.single().name)
        model.search("A"); model.filter("7")
        accounts.values.value = account("B"); runCurrent()
        assertEquals("B", model.uiState.value.visible.single().name)
        assertEquals("", model.uiState.value.query); assertNull(model.uiState.value.categoryId)
        assertEquals(2, catalog.loads)
        model.load(VodKind.MOVIE); runCurrent()
        assertEquals(2, catalog.loads)
    }

    @Test fun anEpgOnlySettingsEditDoesNotDownloadTheCatalogAgain() = runTest(dispatcher) {
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        accounts.values.value = accounts.values.value.copy(epgUrl = "https://synthetic.invalid/guide.xml")
        runCurrent(); assertEquals(1, catalog.loads)
    }

    @Test fun scopedCatalogAutomaticallyLoadsOneCategoryAndCancelsStaleCategoryResults() = runTest(dispatcher) {
        catalog.catalogBlock = { _, _ -> scoped() }
        catalog.categoryBlock = { id ->
            if (id == "7") withContext(NonCancellable) { delay(100) }
            listOf(VodItem(id, "Movie $id", id))
        }
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        assertTrue(model.uiState.value.categoryLoading)
        model.filter("8"); runCurrent()
        assertEquals("8", model.uiState.value.visible.single().id)
        advanceTimeBy(101); runCurrent()
        assertEquals("8", model.uiState.value.visible.single().id)
        assertEquals(listOf("7", "8"), catalog.categoryRequests)
    }

    @Test fun categoryErrorsCanBeRetriedWithoutDiscardingCategories() = runTest(dispatcher) {
        catalog.catalogBlock = { _, _ -> scoped() }
        catalog.categoryBlock = { throw java.io.IOException("synthetic") }
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        assertTrue(model.uiState.value.categoryError)
        assertEquals(2, model.uiState.value.catalog.categories.size)
        catalog.categoryBlock = { listOf(VodItem("42", "Recovered", it)) }
        model.filter("7", retry = true); runCurrent()
        assertFalse(model.uiState.value.categoryError)
        assertEquals("Recovered", model.uiState.value.visible.single().name)
    }

    @Test fun lateDetailsFromTheOldAccountCannotReappearAfterAnAccountSwitch() = runTest(dispatcher) {
        catalog.detailsBlock = { withContext(NonCancellable) { delay(100) }; VodDetails(it) }
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        model.open(model.uiState.value.catalog.items.single()); runCurrent()
        accounts.values.value = account("B"); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertNull(model.uiState.value.selected); assertNull(model.uiState.value.details)
        assertEquals("B", model.uiState.value.visible.single().name)
    }

    @Test fun refreshRetriesAFailedCatalogInsteadOfKeepingTheLoadedKindErrorForever() = runTest(dispatcher) {
        catalog.catalogBlock = { _, _ -> throw java.io.IOException("synthetic") }
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        assertTrue(model.uiState.value.error)
        catalog.catalogBlock = { _, _ -> VodCatalog(emptyList(), listOf(VodItem("1", "Recovered", null))) }
        model.load(VodKind.MOVIE, refresh = true); runCurrent()
        assertFalse(model.uiState.value.error); assertEquals("Recovered", model.uiState.value.visible.single().name)
    }

    @Test fun categoryFilteringIncludesEveryCategoryAssignedToATitle() = runTest(dispatcher) {
        catalog.catalogBlock = { _, _ -> VodCatalog(emptyList(),
            listOf(VodItem("1", "Shared", "7", categoryIds = listOf("7", "8")))) }
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        model.filter("8"); assertEquals("Shared", model.uiState.value.visible.single().name)
    }

    @Test fun switchingToAnUnsupportedSourceClearsThePreviousSubscriptionCatalog() = runTest(dispatcher) {
        val model = model(); model.load(VodKind.MOVIE); runCurrent()
        accounts.values.value = AppSettings(m3uUrl = "https://synthetic.invalid/list.m3u"); runCurrent()
        assertTrue(model.uiState.value.unavailable); assertTrue(model.uiState.value.catalog.items.isEmpty())
    }

    private fun scoped() = VodCatalog(listOf(VodCategory("7", "Seven"), VodCategory("8", "Eight")),
        emptyList(), categoryScoped = true)
}