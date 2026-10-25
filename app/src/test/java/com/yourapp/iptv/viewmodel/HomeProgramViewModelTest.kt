package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.EpgSchedule
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.domain.repository.LiveEpgRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeProgramViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = object : SettingsRepository {
        val values = MutableStateFlow(AppSettings())
        override fun observeSettings() = values
        override suspend fun saveSettings(settings: AppSettings) { values.value = settings }
    }
    private val a = Channel("a", "A", streamUrl = "https://example.invalid/a")
    private val b = Channel("b", "B", streamUrl = "https://example.invalid/b")

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun rapidSelectionsOnlyRequestTheSettledChannel() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val model = HomeProgramViewModel(settings, fake { channel, _ ->
            calls += channel.id; EpgSchedule(emptyList())
        })
        backgroundScope.launch { model.uiState.collect() }
        model.selectChannel(a); runCurrent(); advanceTimeBy(200)
        model.selectChannel(b); runCurrent(); advanceTimeBy(351); runCurrent()
        assertEquals(listOf("b"), calls)
        assertTrue(model.uiState.value.matches(b))
        assertFalse(model.uiState.value.matches(a))
    }

    @Test fun switchingSourcesWithTheSameIdCannotPublishThePreviousSchedule() = runTest(dispatcher) {
        var cancelled = false
        val otherSource = a.copy(streamUrl = "https://example.invalid/other/a")
        val model = HomeProgramViewModel(settings, fake { channel, _ ->
            if (channel.streamUrl == a.streamUrl) {
                try { delay(10_000) } finally { cancelled = true }
            }
            EpgSchedule(listOf(Program("New source", 0, Long.MAX_VALUE)))
        })
        backgroundScope.launch { model.uiState.collect() }
        model.selectChannel(a); runCurrent(); advanceTimeBy(351); runCurrent()
        model.selectChannel(otherSource); runCurrent(); advanceTimeBy(351); runCurrent()
        assertTrue(cancelled)
        assertFalse(model.uiState.value.matches(a))
        assertTrue(model.uiState.value.matches(otherSource))
        assertEquals("New source", model.uiState.value.schedule.programs.single().title)
    }

    @Test fun leavingHomeCancelsRequestsAndDoesNotPollInTheBackground() = runTest(dispatcher) {
        var cancelled = false
        var calls = 0
        val model = HomeProgramViewModel(settings, fake { _, _ ->
            calls++
            try { delay(10_000) } finally { cancelled = true }
            EpgSchedule(emptyList())
        })
        model.selectChannel(a); advanceTimeBy(1_000); runCurrent()
        assertEquals(0, calls)
        val observer = backgroundScope.launch { model.uiState.collect() }
        runCurrent(); advanceTimeBy(351); runCurrent()
        observer.cancel(); model.selectChannel(null); runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertTrue(cancelled)
        assertEquals(1, calls)
    }

    @Test fun unsupportedGuideDoesNotPollOrPreventTheNextChannelLoading() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val model = HomeProgramViewModel(settings, fake { channel, _ ->
            calls += channel.id
            if (channel == a) null else EpgSchedule(listOf(Program("Available", 0, Long.MAX_VALUE)))
        })
        backgroundScope.launch { model.uiState.collect() }
        model.selectChannel(a); runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(listOf("a"), calls)
        assertTrue(model.uiState.value.schedule.programs.isEmpty())
        model.selectChannel(b); runCurrent(); advanceTimeBy(351); runCurrent()
        assertEquals("Available", model.uiState.value.schedule.programs.single().title)
    }

    @Test fun favoritesDoNotReloadButAccountChangesDo() = runTest(dispatcher) {
        var calls = 0
        val model = HomeProgramViewModel(settings, fake { _, _ -> calls++; EpgSchedule(emptyList()) })
        backgroundScope.launch { model.uiState.collect() }
        model.selectChannel(a); runCurrent(); advanceTimeBy(351); runCurrent()
        model.selectChannel(a.copy(isFavorite = true)); runCurrent(); advanceTimeBy(500); runCurrent()
        assertEquals(1, calls)
        settings.saveSettings(AppSettings(xtreamUsername = "synthetic account"))
        runCurrent(); advanceTimeBy(351); runCurrent()
        assertEquals(2, calls)
    }

    private fun fake(load: suspend (Channel, AppSettings) -> EpgSchedule?): LiveEpgRepository =
        object : LiveEpgRepository {
            override suspend fun load(channel: Channel, settings: AppSettings) = load.invoke(channel, settings)
        }
}