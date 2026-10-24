package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.WatchHistoryEntry
import com.yourapp.iptv.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeQuickAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val firstKey = "a".repeat(64)
    private val secondKey = "b".repeat(64)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private class FakeHistory : WatchHistoryRepository {
        val flows = mutableMapOf<String, MutableStateFlow<List<WatchHistoryEntry>>>()
        override fun observe(sourceKey: String): MutableStateFlow<List<WatchHistoryEntry>> =
            flows.getOrPut(sourceKey) { MutableStateFlow(emptyList()) }
        override fun recordStarted(sourceKey: String, entry: WatchHistoryEntry) = Unit
        override suspend fun remove(sourceKey: String, channelId: String) = Unit
        override suspend fun clear(sourceKey: String) = Unit
        fun emit(key: String, vararg ids: String) {
            observe(key).value = ids.mapIndexed { index, id ->
                WatchHistoryEntry(id, index.toLong())
            }
        }
    }

    @Test fun switchingSourceImmediatelyDropsOldHistoryAndIgnoresLateEmissions() = runTest(dispatcher) {
        val history = FakeHistory()
        val model = HomeQuickAccessViewModel(history).also { store.put("quick", it) }
        model.bind(firstKey); runCurrent()
        history.emit(firstKey, "alpha", "beta"); runCurrent()
        assertEquals(listOf("alpha", "beta"), model.uiState.value.channelIds)
        model.bind(secondKey)
        assertEquals(secondKey, model.uiState.value.sourceKey)
        assertTrue(model.uiState.value.channelIds.isEmpty())
        history.emit(firstKey, "old"); history.emit(secondKey, "new"); runCurrent()
        assertEquals(listOf("new"), model.uiState.value.channelIds)
        model.bind(null)
        assertNull(model.uiState.value.sourceKey)
        assertTrue(model.uiState.value.channelIds.isEmpty())
        history.emit(secondKey, "must-not-reappear"); runCurrent()
        assertTrue(model.uiState.value.channelIds.isEmpty())
    }

    @Test fun invalidOrMissingSourceNeverStartsARepositoryRead() = runTest(dispatcher) {
        val history = FakeHistory()
        val model = HomeQuickAccessViewModel(history).also { store.put("quick", it) }
        model.bind("invalid")
        runCurrent()
        assertNull(model.uiState.value.sourceKey)
        assertTrue(history.flows.isEmpty())
    }

    @Test fun oldAndRepeatedIdentifiersAreBoundedToEightTiles() = runTest(dispatcher) {
        val history = FakeHistory()
        val model = HomeQuickAccessViewModel(history).also { store.put("quick", it) }
        model.bind(firstKey); runCurrent()
        history.emit(firstKey, *(0..18).map { "id-$it" }.toTypedArray(), "id-0")
        runCurrent()
        assertEquals((0..7).map { "id-$it" }, model.uiState.value.channelIds)
    }
}