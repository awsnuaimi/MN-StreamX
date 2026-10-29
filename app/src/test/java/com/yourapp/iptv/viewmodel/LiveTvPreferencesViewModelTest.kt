package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.LiveTvPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvPreferencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    private val a = "a".repeat(64); private val b = "b".repeat(64)
    private class Repository : LiveTvPreferencesRepository {
        val rows = mutableMapOf<String, MutableStateFlow<LiveTvPreferences>>()
        var failRead = false; var failWrite = false; var writes = 0
        var release: CompletableDeferred<Unit>? = null
        fun row(scope: String) = rows.getOrPut(scope) { MutableStateFlow(LiveTvPreferences()) }
        override fun observe(scope: String) = flow { if (failRead) throw IOException(); emitAll(row(scope)) }
        override suspend fun update(scope: String, change: (LiveTvPreferences) -> LiveTvPreferences) {
            writes++; release?.await(); if (failWrite) throw IOException(); row(scope).value = change(row(scope).value)
        }
    }
    private val repository = Repository()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun close() { owner.clear(); Dispatchers.resetMain() }
    private fun model() = LiveTvPreferencesViewModel(repository).also { owner.put("personal", it) }
    @Test fun durableAcknowledgmentPrecedesDisplayAndRepeatedClicksDoNotQueueWrites() = runTest(dispatcher) {
        val model = model(); model.bind(a); runCurrent()
        repository.release = CompletableDeferred()
        model.change(a) { it.copy(sort = LiveTvSort.NAME) }; model.change(a) { it.showAll() }; runCurrent()
        assertTrue(model.state.value.saving); assertEquals(LiveTvSort.PROVIDER, model.state.value.value.sort); assertEquals(1, repository.writes)
        repository.release!!.complete(Unit); advanceUntilIdle()
        assertFalse(model.state.value.saving); assertEquals(LiveTvSort.NAME, model.state.value.value.sort)
    }
    @Test fun switchClearsEarlierDataAndStaleCallbacksCannotWriteTheNewOwnersPreferences() = runTest(dispatcher) {
        repository.row(a).value = LiveTvPreferences(hiddenChannels = setOf("secret"))
        val model = model(); model.bind(a); runCurrent(); model.bind(b)
        assertFalse(model.state.value.ready); assertTrue(model.state.value.value.hiddenChannels.isEmpty())
        model.change(a) { it.copy(hiddenChannels = setOf("late")) }; advanceUntilIdle()
        assertEquals(0, repository.writes); assertEquals(b, model.state.value.scope); assertTrue(model.state.value.value.hiddenChannels.isEmpty())
    }
    @Test fun storageFailureKeepsTheOldSelectionAndAllowsAnExplicitRetry() = runTest(dispatcher) {
        val model = model(); model.bind(a); runCurrent(); repository.failWrite = true
        model.change(a) { it.copy(sort = LiveTvSort.NAME) }; advanceUntilIdle()
        assertTrue(model.state.value.error); assertEquals(LiveTvSort.PROVIDER, model.state.value.value.sort)
        repository.failWrite = false; model.change(a) { it.copy(sort = LiveTvSort.NAME) }; advanceUntilIdle()
        assertFalse(model.state.value.error); assertEquals(LiveTvSort.NAME, model.state.value.value.sort)
    }
    @Test fun failedReadsDoNotEnableEditsOrExposeADefaultListUntilRetrySucceeds() = runTest(dispatcher) {
        repository.failRead = true; val model = model(); model.bind(a); advanceUntilIdle()
        assertFalse(model.state.value.ready); assertTrue(model.state.value.error)
        model.change(a) { it.showAll() }; assertEquals(0, repository.writes)
        repository.failRead = false; model.bind(a, retry = true); advanceUntilIdle()
        assertTrue(model.state.value.ready); assertFalse(model.state.value.error)
    }
    @Test fun aCancelledWriteFromThePreviousSourceDoesNotClearTheNewSourcesBusyFlag() = runTest(dispatcher) {
        val model = model(); model.bind(a); runCurrent(); repository.release = CompletableDeferred()
        model.change(a) { it.copy(sort = LiveTvSort.NAME) }; runCurrent(); model.bind(b); runCurrent()
        model.change(b) { it.copy(sort = LiveTvSort.FAVORITES) }; runCurrent()
        assertEquals(b, model.state.value.scope); assertTrue(model.state.value.saving)
        repository.release!!.complete(Unit); advanceUntilIdle()
        assertEquals(LiveTvSort.FAVORITES, model.state.value.value.sort); assertFalse(model.state.value.saving)
        assertEquals(LiveTvSort.PROVIDER, repository.row(a).value.sort)
    }
}