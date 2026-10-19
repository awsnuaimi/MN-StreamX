package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.EpgSchedule
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.domain.repository.LiveEpgRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.ui.state.PlayerEpgUiState
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
class PlayerEpgViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettings()
    private val a = Channel("a", "A", streamUrl = "https://unit.test/a")
    private val b = Channel("b", "B", streamUrl = "https://unit.test/b")
    private val epoch = 1700000000000L
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun rapidChannelSwitchesOnlyRequestTheFinalSelection() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val repository = fake { channel, _ -> calls += channel.id; EpgSchedule(emptyList()) }
        val model = PlayerEpgViewModel(settings, repository)
        backgroundScope.launch { model.uiState.collect() }
        model.selectChannel(a)
        runCurrent()
        advanceTimeBy(200)
        model.selectChannel(b)
        runCurrent()
        advanceTimeBy(351)
        runCurrent()
        assertEquals(listOf("b"), calls)
        assertEquals("b", model.uiState.value.channelId)
    }

    @Test fun changingChannelCancelsTheOldRequestAndCannotPublishItsPrograms() = runTest(dispatcher) {
        var cancelled = false
        val repository = fake { channel, _ ->
            if (channel.id == "a") {
                try { delay(10_000) } finally { cancelled = true }
            }
            EpgSchedule(listOf(Program(channel.name, epoch, epoch + 60_000)))
        }
        val model = PlayerEpgViewModel(settings, repository)
        backgroundScope.launch { model.uiState.collect() }
        model.selectChannel(a)
        runCurrent(); advanceTimeBy(351); runCurrent()
        model.selectChannel(b)
        runCurrent(); advanceTimeBy(351); runCurrent()
        assertTrue(cancelled)
        assertEquals("b", model.uiState.value.channelId)
        assertEquals("B", model.uiState.value.schedule.programs.single().title)
    }

    @Test fun collectingControlsTheRequestLifetimeAndSettingsChangesReloadTheSchedule() = runTest(dispatcher) {
        var calls = 0
        val model = PlayerEpgViewModel(settings, fake { _, _ -> calls++; EpgSchedule(emptyList()) })
        model.selectChannel(a)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, calls)
        val observer = backgroundScope.launch { model.uiState.collect() }
        runCurrent(); advanceTimeBy(351); runCurrent()
        assertEquals(1, calls)
        settings.values.value = AppSettings(xtreamUsername = "new synthetic account")
        runCurrent(); advanceTimeBy(351); runCurrent()
        assertEquals(2, calls)
        observer.cancel()
        runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(2, calls)
    }

    @Test fun programBoundaryAndProgressAdvanceWithoutANetworkRequestEveryTick() = runTest(dispatcher) {
        var calls = 0
        var state = PlayerEpgUiState()
        val repository = fake { _, _ ->
            calls++
            EpgSchedule(listOf(Program("First", epoch, epoch + 30_000),
                Program("Next", epoch + 30_000, epoch + 90_000)))
        }
        backgroundScope.launch {
            observePlayerEpg(a, AppSettings(), repository) { epoch + testScheduler.currentTime }
                .collect { state = it }
        }
        runCurrent(); advanceTimeBy(351); runCurrent()
        assertEquals("First", state.schedule.current(state.nowMillis)?.title)
        advanceTimeBy(30_000); runCurrent()
        assertEquals("Next", state.schedule.current(state.nowMillis)?.title)
        assertTrue(state.schedule.progress(state.nowMillis) > 0f)
        assertEquals(1, calls)
    }

    @Test fun guideFailuresAreReadableAndAreRetriedWithoutChangingThePlayer() = runTest(dispatcher) {
        var calls = 0
        var state = PlayerEpgUiState()
        val repository = fake { _, _ ->
            if (++calls == 1) error("url?password=never-display-this")
            EpgSchedule(listOf(Program("Recovered", epoch, epoch + 300_000)))
        }
        backgroundScope.launch {
            observePlayerEpg(a, AppSettings(), repository) { epoch + testScheduler.currentTime }
                .collect { state = it }
        }
        runCurrent(); advanceTimeBy(351); runCurrent()
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertFalse(state.errorMessage!!.contains("never-display-this"))
        advanceTimeBy(60_001); runCurrent()
        assertEquals(2, calls)
        assertNull(state.errorMessage)
        assertEquals("Recovered", state.schedule.programs.single().title)
        assertEquals("a", state.channelId)
    }

    @Test fun unsupportedChannelsStopPollingUntilTheSelectionChanges() = runTest(dispatcher) {
        var calls = 0
        var state = PlayerEpgUiState()
        backgroundScope.launch {
            observePlayerEpg(a, AppSettings(), fake { _, _ -> calls++; null })
                .collect { state = it }
        }
        runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertFalse(state.isSupported)
        assertFalse(state.isLoading)
        assertEquals(1, calls)
    }

    @Test fun favoriteFlagChangesDoNotRestartTheEpgRequest() = runTest(dispatcher) {
        var calls = 0
        val model = PlayerEpgViewModel(settings, fake { _, _ -> calls++; EpgSchedule(emptyList()) })
        backgroundScope.launch { model.uiState.collect() }
        model.selectChannel(a)
        runCurrent(); advanceTimeBy(351); runCurrent()
        model.selectChannel(a.copy(isFavorite = true))
        runCurrent(); advanceTimeBy(500); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun previewUsesAnIndependentSelectionAndStopsRequestsWhenCardsClose() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val repository = fake { channel, _ -> calls += channel.id; EpgSchedule(emptyList()) }
        val active = PlayerEpgViewModel(settings, repository)
        val preview = PlayerEpgViewModel(settings, repository)
        backgroundScope.launch { active.uiState.collect() }
        active.selectChannel(a); runCurrent(); advanceTimeBy(351); runCurrent()
        val previewObserver = backgroundScope.launch { preview.uiState.collect() }
        preview.selectChannel(b); runCurrent(); advanceTimeBy(200); runCurrent()
        preview.selectChannel(a); runCurrent(); advanceTimeBy(351); runCurrent()
        assertEquals("a", active.uiState.value.channelId)
        assertEquals("a", preview.uiState.value.channelId)
        assertFalse(calls.contains("b"))
        previewObserver.cancel(); runCurrent()
        preview.selectChannel(b); advanceTimeBy(500); runCurrent()
        assertFalse(calls.contains("b"))
    }

    @Test fun closingThePreviewCancelsItsInflightRequestWithoutStoppingTheActiveEpg() = runTest(dispatcher) {
        var previewCancelled = false
        val repository = fake { channel, _ ->
            if (channel.id == "b") try { delay(10000) } finally { previewCancelled = true }
            EpgSchedule(emptyList())
        }
        val active = PlayerEpgViewModel(settings, repository)
        val preview = PlayerEpgViewModel(settings, repository)
        backgroundScope.launch { active.uiState.collect() }
        active.selectChannel(a)
        val observer = backgroundScope.launch { preview.uiState.collect() }
        preview.selectChannel(b); runCurrent(); advanceTimeBy(351); runCurrent()
        observer.cancel(); runCurrent()
        assertTrue(previewCancelled)
        assertEquals("a", active.uiState.value.channelId)
        assertFalse(active.uiState.value.isLoading)
    }

    private fun fake(load: suspend (Channel, AppSettings) -> EpgSchedule?): LiveEpgRepository =
        object : LiveEpgRepository {
            override suspend fun load(channel: Channel, settings: AppSettings) = load.invoke(channel, settings)
        }

    private class FakeSettings : SettingsRepository {
        val values = MutableStateFlow(AppSettings())
        override fun observeSettings() = values
        override suspend fun saveSettings(settings: AppSettings) { values.value = settings }
    }
}