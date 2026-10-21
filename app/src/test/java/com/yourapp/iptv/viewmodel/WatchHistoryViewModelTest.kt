package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.WatchHistoryError
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val settings = HistorySettings()
    private val history = HistoryMemory()
    private val channels = HistoryChannels()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }

    @Test fun readsOnlyTheSavedIdsUsesCurrentMetadataAndSkipsDeletedChannels() = runTest(dispatcher) {
        history.records(settings.key).value = listOf(WatchHistoryEntry("deleted", 3), WatchHistoryEntry("b", 2), WatchHistoryEntry("a", 1))
        channels.rows = channels.rows.map { it.copy(name = "Updated ${it.id}", streamUrl = "https://unit.test/new-${it.id}") }
        val model = model(); runCurrent()
        assertEquals(listOf("b", "a"), model.uiState.value.rows.map { it.channel.id })
        assertEquals("Updated b", model.uiState.value.rows.first().channel.name)
        assertEquals(setOf("deleted", "b", "a"), channels.requested)
        assertEquals(0, channels.allReads); assertEquals(1, channels.lookupReads)
        assertEquals(3, model.uiState.value.totalRecords)
    }
    @Test fun anEmptyHistoryDoesNotReadChannelData() = runTest(dispatcher) {
        val model = model(); runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertEquals(0, channels.lookupReads); assertEquals(0, channels.allReads)
    }
    @Test fun lateResultsCannotCrossAccountsEvenWhenIdsAreTheSame() = runTest(dispatcher) {
        val first = settings.value.value
        val old = CompletableDeferred<List<Channel>>()
        history.records(settings.key).value = listOf(WatchHistoryEntry("a", 1))
        channels.customLookup = { source, _ ->
            if (source == first) withContext(NonCancellable) { old.await() }
            else listOf(channels.rows.first().copy(name = "Other account", streamUrl = "https://unit.test/other"))
        }
        val model = model(); runCurrent()
        settings.value.value = first.copy(m3uUrl = "https://unit.test/other-list")
        history.records(settings.key).value = listOf(WatchHistoryEntry("a", 2)); runCurrent()
        assertEquals("Other account", model.uiState.value.rows.single().channel.name)
        old.complete(listOf(channels.rows.first())); runCurrent()
        assertEquals("Other account", model.uiState.value.rows.single().channel.name)
        assertNull(model.playbackChannel(PlaylistCacheKey.of(first), "a"))
    }
    @Test fun aNewHistoryEmissionCancelsAnOlderLookup() = runTest(dispatcher) {
        val old = CompletableDeferred<List<Channel>>()
        history.records(settings.key).value = listOf(WatchHistoryEntry("a", 1))
        channels.customLookup = { _, ids ->
            if ("a" in ids) withContext(NonCancellable) { old.await() } else channels.rows.filter { it.id in ids }
        }
        val model = model(); runCurrent()
        history.records(settings.key).value = listOf(WatchHistoryEntry("b", 2)); runCurrent()
        assertEquals("b", model.uiState.value.rows.single().channel.id)
        old.complete(listOf(channels.rows.first())); runCurrent()
        assertEquals("b", model.uiState.value.rows.single().channel.id)
    }
    @Test fun removeAndClearAffectHistoryOnlyAndAnOldSourceCannotEditTheNewOne() = runTest(dispatcher) {
        history.records(settings.key).value = listOf(WatchHistoryEntry("b", 2), WatchHistoryEntry("a", 1))
        val model = model(); runCurrent()
        model.remove(settings.key, "b"); runCurrent()
        assertEquals(listOf("a"), model.uiState.value.rows.map { it.channel.id })
        assertEquals(3, channels.rows.size)
        model.clear("old-source"); runCurrent(); assertEquals(1, model.uiState.value.rows.size)
        model.clear(settings.key); runCurrent(); assertTrue(model.uiState.value.rows.isEmpty())
    }
    @Test fun failedWritesKeepTheListAndAnExplicitRetrySucceeds() = runTest(dispatcher) {
        history.records(settings.key).value = listOf(WatchHistoryEntry("a", 1))
        val model = model(); runCurrent()
        history.failWrite = true; model.remove(settings.key, "a"); runCurrent()
        assertEquals(WatchHistoryError.WRITE, model.uiState.value.error)
        assertEquals("a", model.uiState.value.rows.single().channel.id)
        history.failWrite = false; model.remove(settings.key, "a"); runCurrent()
        assertTrue(model.uiState.value.rows.isEmpty()); assertFalse(model.uiState.value.isSaving)
    }
    @Test fun failedReadsAndMissingSourcesEndLoadingWithoutCrashes() = runTest(dispatcher) {
        history.failRead = true
        val model = model(); runCurrent()
        assertEquals(WatchHistoryError.READ, model.uiState.value.error)
        history.failRead = false; model.reload(); runCurrent(); assertNull(model.uiState.value.error)
        settings.value.value = AppSettings(); runCurrent()
        assertTrue(model.uiState.value.needsSource); assertFalse(model.uiState.value.isLoading)
    }
    @Test fun aChannelLookupFailureHasARetryableError() = runTest(dispatcher) {
        history.records(settings.key).value = listOf(WatchHistoryEntry("a", 1)); channels.fail = true
        val model = model(); runCurrent()
        assertEquals(WatchHistoryError.CHANNELS, model.uiState.value.error)
        channels.fail = false; model.reload(); runCurrent()
        assertEquals("a", model.uiState.value.rows.single().channel.id)
    }
    @Test fun closingTheHistoryStopsLookupsAndReopeningReadsTheLatestRows() = runTest(dispatcher) {
        history.records(settings.key).value = listOf(WatchHistoryEntry("a", 1))
        val model = model(); runCurrent()
        model.pause(); runCurrent()
        val reads = channels.lookupReads
        history.records(settings.key).value = listOf(WatchHistoryEntry("b", 2)); runCurrent()
        assertEquals(reads, channels.lookupReads)
        model.resume(); runCurrent()
        assertEquals("b", model.uiState.value.rows.single().channel.id)
        assertEquals(reads + 1, channels.lookupReads)
    }
    private fun model() = WatchHistoryViewModel(settings, history, channels, dispatcher).also { owner.put("history", it) }
}