package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.HomeAppearanceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeChannelLayoutViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun start() { Dispatchers.setMain(dispatcher) }
    @After fun stop() { store.clear(); Dispatchers.resetMain() }
    private class Repository : HomeAppearanceRepository {
        val state = MutableStateFlow(HomeAppearance())
        val writes = mutableListOf<HomeAppearance>()
        var fail = false
        override fun observe() = state
        override suspend fun save(value: HomeAppearance) {
            writes += value; delay(100)
            if (fail) throw IOException()
            if (state.value.profileId == value.profileId) state.value = value
        }
    }
    private fun model(repository: Repository) = HomeAppearanceViewModel(repository).also { store.put("reader", it) }

    @Test fun acceptedLayoutWriteIsSerializedAndKeepsThemeLogosAndFiveColumnPreference() = runTest(dispatcher) {
        val repo = Repository().apply { state.value = HomeAppearance(columns = 5, theme = HomeTheme.RED,
            isNightMode = true, showChannelLogos = false) }
        val old = repo.state.value; val vm = model(repo); runCurrent()
        vm.setChannelLayout(HomeChannelLayout.LIST); vm.setChannelLayout(HomeChannelLayout.LIST); vm.toggleNightMode()
        assertTrue(vm.themeSaving.value); runCurrent(); assertEquals(1, repo.writes.size)
        advanceTimeBy(101); runCurrent()
        assertEquals(old.copy(channelLayout = HomeChannelLayout.LIST), vm.appearance.value)
        vm.setChannelLayout(HomeChannelLayout.LIST); runCurrent(); assertEquals(1, repo.writes.size)
        vm.setChannelLayout(HomeChannelLayout.GRID); advanceTimeBy(101); runCurrent()
        assertEquals(old, vm.appearance.value); assertFalse(vm.themeSaving.value)
    }

    @Test fun failedSaveKeepsPreviousLayoutAndRetryAppliesTheSameChoice() = runTest(dispatcher) {
        val repo = Repository().apply { fail = true }; val vm = model(repo); runCurrent()
        vm.setChannelLayout(HomeChannelLayout.LIST); advanceTimeBy(101); runCurrent()
        assertEquals(HomeChannelLayout.GRID, vm.appearance.value!!.channelLayout)
        assertTrue(vm.channelLayoutError.value); assertFalse(vm.themeError.value); assertFalse(vm.themeSaving.value)
        repo.fail = false; vm.setChannelLayout(HomeChannelLayout.LIST); advanceTimeBy(101); runCurrent()
        assertEquals(HomeChannelLayout.LIST, vm.appearance.value!!.channelLayout)
        assertFalse(vm.channelLayoutError.value)
    }

    @Test fun switchingProfilesDuringASaveCannotPublishTheOldProfileAsTheNewOne() = runTest(dispatcher) {
        val repo = Repository(); val vm = model(repo); runCurrent()
        vm.setChannelLayout(HomeChannelLayout.LIST); runCurrent()
        val other = HomeAppearance(profileId = "12345678-1234-1234-1234-123456789abc", columns = 2)
        repo.state.value = other; runCurrent(); advanceTimeBy(101); runCurrent()
        assertEquals("default", repo.writes.single().profileId)
        assertEquals(other, vm.appearance.value)
    }
}