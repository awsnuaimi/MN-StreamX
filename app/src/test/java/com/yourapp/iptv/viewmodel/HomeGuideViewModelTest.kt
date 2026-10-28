package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.profile.ProfileScope
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeGuideViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private class Settings : SettingsRepository {
        val state = MutableStateFlow(AppSettings(source = PlaylistSource.XTREAM,
            xtreamServer = "https://synthetic.invalid", xtreamUsername = "synthetic", xtreamPassword = "synthetic"))
        override fun observeSettings() = state
        override suspend fun saveSettings(settings: AppSettings) { state.value = settings }
    }
    private class Access : ParentalControl by ParentalControl.Disabled {
        override val state = MutableStateFlow(ParentalState(ready = true))
    }
    private val settings = Settings();private val access = Access()
    private fun channel(i: Int) = Channel("$i", "Channel $i", streamUrl = "https://synthetic.invalid/$i")
    private fun fake(block: suspend (Channel, AppSettings) -> EpgSchedule?) = object : LiveEpgRepository {
        override suspend fun load(channel: Channel, settings: AppSettings) = block(channel, settings)
    }
    private fun model(repository: LiveEpgRepository) = HomeGuideViewModel(settings, repository, access).also { store.put("vm", it) }
    @Before fun start() { Dispatchers.setMain(dispatcher) }
    @After fun stop() { store.clear(); Dispatchers.resetMain() }

    @Test fun visibleWindowIsCappedAndNetworkConcurrencyNeverExceedsTwo() = runTest(dispatcher) {
        var active = 0;var maximum = 0;val calls = mutableListOf<String>()
        val vm = model(fake { channel, _ ->
            calls += channel.id;active++;maximum = maxOf(maximum, active)
            try { delay(100); EpgSchedule(listOf(Program(channel.name, 0, Long.MAX_VALUE))) } finally { active-- }
        })
        backgroundScope.launch { vm.uiState.collect() }
        vm.bind((0 until 7490).map(::channel));runCurrent();advanceTimeBy(1_000);runCurrent()
        assertEquals(8, calls.size);assertEquals(2, maximum);assertEquals(8, vm.uiState.value.entries.size)
        assertEquals(ProfileScope.of(settings.state.value), vm.uiState.value.sourceKey)
    }

    @Test fun quickScrollingCancelsIntermediateSelectionsAndFavoriteChangesDoNotReload() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val vm = model(fake { row, _ -> calls += row.id;EpgSchedule(emptyList()) })
        backgroundScope.launch { vm.uiState.collect() }
        vm.bind(listOf(channel(0)));runCurrent();advanceTimeBy(200)
        vm.bind(listOf(channel(1)));runCurrent();advanceTimeBy(351);runCurrent()
        assertEquals(listOf("1"), calls)
        vm.bind(listOf(channel(1).copy(isFavorite = true)));advanceTimeBy(500);runCurrent()
        assertEquals(listOf("1"), calls)
    }

    @Test fun lockingDuringANonCooperativeFetchCannotPublishItsLateResult() = runTest(dispatcher) {
        val entered = CompletableDeferred<Unit>();val release = CompletableDeferred<Unit>()
        val vm = model(fake { _, _ ->
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
            EpgSchedule(listOf(Program("Old protected programme", 0, Long.MAX_VALUE)))
        })
        backgroundScope.launch { vm.uiState.collect() }
        vm.bind(listOf(channel(0)));runCurrent();advanceTimeBy(351);runCurrent();assertTrue(entered.isCompleted)
        access.state.value = ParentalState(ready = true, configured = true, rules = ParentalRules(setOf(ParentalSection.LIVE)), epoch = 1)
        runCurrent();release.complete(Unit);runCurrent()
        assertTrue(vm.uiState.value.entries.isEmpty());assertEquals(1L, vm.uiState.value.accessEpoch)
    }

    @Test fun profileAndSourceChangesRejectAnOtherwiseIdenticalChannelIdAndUrl() = runTest(dispatcher) {
        val vm = model(fake { _, account -> EpgSchedule(listOf(Program(account.profileId, 0, Long.MAX_VALUE))) })
        backgroundScope.launch { vm.uiState.collect() }
        val row = channel(0);vm.bind(listOf(row));runCurrent();advanceTimeBy(351);runCurrent()
        val old = vm.uiState.value
        val other = "12345678-1234-1234-1234-123456789abc"
        settings.state.value = settings.state.value.copy(profileId = other)
        access.state.value = access.state.value.copy(profileId = other, epoch = 1)
        runCurrent();advanceTimeBy(351);runCurrent()
        val owner = ProfileScope.of(settings.state.value)
        assertFalse(old.forChannel(row, owner, 1).matches(row))
        assertEquals(other, vm.uiState.value.forChannel(row, owner, 1).schedule.programs.single().title)
    }

    @Test fun leavingHomeCancelsPollingAndResetsTheReplayedWindow() = runTest(dispatcher) {
        var calls = 0;var cancelled = false
        val vm = model(fake { _, _ ->
            calls++;try { delay(10_000) } finally { cancelled = true };EpgSchedule(emptyList())
        })
        val collector = backgroundScope.launch { vm.uiState.collect() }
        vm.bind(listOf(channel(0)));runCurrent();advanceTimeBy(351);runCurrent()
        collector.cancel();vm.bind(emptyList());runCurrent();advanceTimeBy(120_000);runCurrent()
        assertTrue(cancelled);assertEquals(1, calls);assertTrue(vm.uiState.value.entries.isEmpty())
    }

    @Test fun oneFailedOrUnsupportedChannelDoesNotHideOtherCardGuides() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val vm = model(fake { row, _ ->
            calls += row.id
            when (row.id) { "0" -> throw IOException();"1" -> null;else -> EpgSchedule(listOf(Program("Available", 0, Long.MAX_VALUE))) }
        })
        backgroundScope.launch { vm.uiState.collect() }
        vm.bind(listOf(channel(0), channel(1), channel(2)));runCurrent();advanceTimeBy(351);runCurrent()
        assertEquals("Available", vm.uiState.value.entries.first { it.channelId == "2" }.schedule.programs.single().title)
        advanceTimeBy(61_000);runCurrent()
        assertEquals(1, calls.count { it == "1" })
    }

    @Test fun browserWindowUsesItsOwnBoundAndRetainsTheTwoRequestLimit() = runTest(dispatcher) {
        var active = 0;var maximum = 0;val calls = mutableListOf<String>()
        val vm = model(fake { row, _ ->
            calls += row.id;active++;maximum = maxOf(maximum, active)
            try { delay(10); EpgSchedule(emptyList()) } finally { active-- }
        })
        backgroundScope.launch { vm.uiState.collect() }
        vm.bind((0 until 7490).map(::channel), Int.MAX_VALUE)
        runCurrent();advanceTimeBy(1_000);runCurrent()
        assertEquals(HomeGuideViewModel.MAX_BROWSER_CHANNELS, calls.size)
        assertEquals(2, maximum)
        vm.bind((100 until 120).map(::channel));runCurrent();advanceTimeBy(1_000);runCurrent()
        assertEquals((100 until 108).map(Int::toString), calls.filter { it.toInt() >= 100 })
    }

    @Test fun clearingTheBrowserWindowCancelsRequestsEvenWhileTheHostStillCollects() = runTest(dispatcher) {
        var calls = 0;var cancelled = 0
        val vm = model(fake { _, _ ->
            calls++;try { delay(30_000);EpgSchedule(emptyList()) } finally { cancelled++ }
        })
        backgroundScope.launch { vm.uiState.collect() }
        vm.bind((0 until 25).map(::channel), HomeGuideViewModel.MAX_BROWSER_CHANNELS)
        runCurrent();advanceTimeBy(351);runCurrent();assertEquals(2, calls)
        vm.bind(emptyList());runCurrent();advanceTimeBy(120_000);runCurrent()
        assertEquals(2, cancelled);assertEquals(2, calls);assertTrue(vm.uiState.value.entries.isEmpty())
    }
}