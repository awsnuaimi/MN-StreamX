package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.WatchHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupPlaybackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun attemptedFlagIsSavedBeforeAsyncWorkAndRestoredModelDoesNotReplay() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val settings = HistorySettings()
        val history = HistoryMemory().apply { records(settings.key).value = listOf(WatchHistoryEntry("a", 1)) }
        val channels = HistoryChannels()
        val model = StartupPlaybackViewModel(StartupPreference(), settings, history, channels, state)
        store.put("first", model); model.initialize(true)
        var plays = 0
        model.onHomeReady(settings.key) { _, _ -> plays++ }
        assertEquals(true, state.get<Boolean>("startupPlaybackAttempted")); runCurrent()
        assertEquals(1, plays)
        val restored = StartupPlaybackViewModel(StartupPreference(), settings, history, channels, state)
        store.put("restored", restored); restored.initialize(true)
        restored.onHomeReady(settings.key) { _, _ -> plays++ }; runCurrent()
        assertEquals(1, plays); assertEquals(1, channels.lookupReads)
    }

    @Test fun restoredActivityAndRepeatedInitializationStayOnHome() = runTest(dispatcher) {
        val settings = HistorySettings()
        val model = StartupPlaybackViewModel(StartupPreference(), settings, HistoryMemory(), HistoryChannels(), SavedStateHandle())
        store.put("startup", model); model.initialize(false); model.initialize(true)
        var plays = 0
        model.onHomeReady(settings.key) { _, _ -> plays++ }; runCurrent()
        assertEquals(0, plays)
    }
}