package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.testing.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val accounts = SearchAccounts()
    private val catalog = SearchCatalogMemory()
    private val history = SearchHistoryMemory()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }
    private fun model(saved: SavedStateHandle = SavedStateHandle()) =
        SearchViewModel(saved, accounts, catalog, history, dispatcher).also { owner.put("search", it) }

    @Test fun catalogsLoadLazilyOnceAndRapidTypingOnlySearchesTheLatestText() = runTest(dispatcher) {
        val model = model(); runCurrent()
        assertTrue(catalog.loads.isEmpty())
        model.updateQuery("s"); advanceTimeBy(100); model.updateQuery("sha"); advanceTimeBy(100)
        assertTrue(catalog.loads.isEmpty())
        model.updateQuery("shared"); advanceTimeBy(221); runCurrent()
        assertEquals(2, catalog.loads.size)
        assertEquals("42", model.state.value.channels.single().id)
        assertEquals("42", model.state.value.movies.results.single().id)
        assertEquals("42", model.state.value.series.results.single().id)
        model.updateQuery("shared movie"); advanceTimeBy(221); runCurrent()
        assertEquals(2, catalog.loads.size)
        assertTrue(model.state.value.channels.isEmpty()); assertEquals(1, model.state.value.movies.results.size)
        assertTrue(model.state.value.series.results.isEmpty())
    }
    @Test fun spacesCanBeTypedBetweenWordsAndHistoryIsWrittenOnlyOnExplicitSubmitOrSelection() = runTest(dispatcher) {
        val model = model(); runCurrent()
        model.updateQuery("Shared "); assertEquals("Shared ", model.state.value.query)
        model.updateQuery("Shared A"); advanceTimeBy(221); runCurrent()
        assertEquals(0, history.writes)
        val selected = model.state.value.channels.single()
        assertTrue(model.chooseChannel(selected, accounts.key)); runCurrent()
        assertEquals(listOf("Shared A"), history.values.value[accounts.key])
        assertEquals(listOf(selected), model.state.value.channels)
    }
    @Test fun switchingAccountsClearsResultsAndQueryAndKeepsEachSearchHistorySeparate() = runTest(dispatcher) {
        val model = model(); runCurrent()
        model.updateQuery("shared A"); model.submit(); runCurrent()
        val a = accounts.key
        val old = model.state.value.channels.single()
        accounts.values.value = searchAccount("B"); runCurrent()
        assertEquals("", model.state.value.query); assertTrue(model.state.value.channels.isEmpty())
        assertTrue(model.state.value.history.isEmpty()); assertFalse(model.chooseChannel(old, a))
        model.updateQuery("shared B"); model.submit(); runCurrent()
        assertEquals(listOf("shared B"), model.state.value.history)
        accounts.values.value = searchAccount("A"); runCurrent()
        assertEquals(listOf("shared A"), model.state.value.history)
        model.clearHistory(); runCurrent()
        assertTrue(model.state.value.history.isEmpty())
        assertEquals(listOf("shared B"), history.values.value[PlaylistCacheKey.of(searchAccount("B"))])
    }
    @Test fun lateOldAccountCatalogsAndDetailsCannotOverwriteTheNewAccount() = runTest(dispatcher) {
        val default = catalog.catalogBlock
        catalog.catalogBlock = { account, kind ->
            if (account.xtreamUsername == "A") withContext(NonCancellable) { delay(1000) }
            default(account, kind)
        }
        val model = model(); runCurrent(); model.updateQuery("shared"); model.submit(); runCurrent()
        accounts.values.value = searchAccount("B"); runCurrent()
        model.updateQuery("shared"); model.submit(); runCurrent()
        assertTrue(model.state.value.movies.results.single().name.endsWith("B"))
        advanceTimeBy(1001); runCurrent()
        assertTrue(model.state.value.movies.results.single().name.endsWith("B"))
        catalog.detailsBlock = { _, _, item -> withContext(NonCancellable) { delay(500) }; VodDetails(item) }
        model.open(VodKind.MOVIE, model.state.value.movies.results.single(), accounts.key); runCurrent()
        assertNotNull(model.details.value.selected)
        accounts.values.value = searchAccount("C"); runCurrent(); advanceTimeBy(501); runCurrent()
        assertNull(model.details.value.selected); assertNull(model.details.value.details)
    }
    @Test fun oneCatalogFailureDoesNotHideOtherSectionsAndCanBeRetried() = runTest(dispatcher) {
        val default = catalog.catalogBlock
        catalog.catalogBlock = { account, kind -> if (kind == VodKind.MOVIE) throw IOException("Synthetic"); default(account, kind) }
        val model = model(); runCurrent(); model.updateQuery("shared"); model.submit(); runCurrent()
        assertTrue(model.state.value.movies.error)
        assertEquals(1, model.state.value.channels.size); assertEquals(1, model.state.value.series.results.size)
        catalog.catalogBlock = default; model.retry(VodKind.MOVIE); runCurrent()
        assertFalse(model.state.value.movies.error); assertEquals(1, model.state.value.movies.results.size)
    }
    @Test fun unsupportedM3uStillSearchesChannelsAndMissingCacheIsNotReadFromAnotherAccount() = runTest(dispatcher) {
        accounts.values.value = AppSettings(m3uUrl = "https://synthetic.invalid/list.m3u")
        val model = model(); runCurrent(); model.updateQuery("shared"); model.submit(); runCurrent()
        assertEquals(1, model.state.value.channels.size)
        assertTrue(model.state.value.movies.unavailable); assertTrue(catalog.loads.isEmpty())
        catalog.channelsBlock = { null }; model.reloadChannels(); runCurrent()
        assertTrue(model.state.value.channelsMissing)
        assertTrue(model.state.value.channels.isEmpty())
    }
    @Test fun theSameSavedSourceRestoresTheQueryButADifferentSourceDoesNot() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("unifiedSearchQuery" to "shared", "unifiedSearchSource" to accounts.key))
        val model = model(saved); runCurrent()
        assertEquals("shared", model.state.value.query); assertEquals(1, model.state.value.channels.size)
        accounts.values.value = searchAccount("B"); runCurrent()
        assertEquals("", model.state.value.query)
    }
    @Test fun pausingCancelsCategoryRequestsAndReturningPreservesTheQueryAndFullCatalogResults() = runTest(dispatcher) {
        val model = model(); runCurrent(); model.updateQuery("shared"); model.submit(); runCurrent()
        val before = model.state.value
        model.setVisible(false); model.setVisible(true); runCurrent()
        assertEquals(before.channels, model.state.value.channels)
        assertEquals(before.movies.results, model.state.value.movies.results)
        assertEquals("shared", model.state.value.query); assertEquals(2, catalog.loads.size)
    }
    @Test fun anEpgOnlyEditDoesNotDiscardTheIndexOrDownloadAgain() = runTest(dispatcher) {
        val model = model(); runCurrent(); model.updateQuery("shared"); model.submit(); runCurrent()
        accounts.values.value = accounts.values.value.copy(epgUrl = "https://synthetic.invalid/epg")
        runCurrent(); assertEquals(2, catalog.loads.size); assertEquals("shared", model.state.value.query)
    }
    @Test fun seriesResultsLoadEpisodesAndHistoryWriteFailureDoesNotPreventOpeningThem() = runTest(dispatcher) {
        val model = model(); runCurrent(); model.updateQuery("shared"); model.submit(); runCurrent()
        history.fail = true
        model.open(VodKind.SERIES, model.state.value.series.results.single(), accounts.key); runCurrent()
        assertEquals("99", model.details.value.details!!.seasons.single().episodes.single().id)
        assertTrue(model.state.value.historyError)
        model.closeDetails(); assertNull(model.details.value.selected)
    }
}