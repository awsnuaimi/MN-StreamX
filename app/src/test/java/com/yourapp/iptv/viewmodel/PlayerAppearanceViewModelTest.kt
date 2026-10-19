package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.PlayerAppearanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerAppearanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun savedChangesReachThePlayerWithoutRecreatingItsModel() = runTest(dispatcher) {
        val repository = FakePlayerAppearanceRepository()
        val model = PlayerAppearanceViewModel(repository).also { store.put("player", it) }
        runCurrent()
        repository.values.value = PlayerAppearance(style = PlayerInfoStyle.CLASSIC, displaySeconds = 8)
        runCurrent()
        assertEquals(PlayerInfoStyle.CLASSIC, model.appearance.value.style)
        assertEquals(8000L, model.appearance.value.timeoutMillis)
        assertEquals(1, repository.reads)
    }

    @Test fun readFailureKeepsLastAppearanceAndRetriesWithoutBusyLooping() = runTest(dispatcher) {
        var attempts = 0
        val recovered = PlayerAppearance(showHints = true)
        val values = MutableStateFlow(recovered)
        val repository = object : PlayerAppearanceRepository {
            override fun observe() = flow {
                attempts++
                if (attempts == 1) {
                    emit(PlayerAppearance(style = PlayerInfoStyle.CLASSIC))
                    throw IOException("Synthetic failure")
                }
                emitAll(values)
            }
            override suspend fun save(appearance: PlayerAppearance) { values.value = appearance }
        }
        val model = PlayerAppearanceViewModel(repository).also { store.put("player", it) }
        runCurrent()
        assertEquals(PlayerInfoStyle.CLASSIC, model.appearance.value.style)
        advanceTimeBy(4999); runCurrent(); assertEquals(1, attempts)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, attempts)
        assertEquals(recovered, model.appearance.value)
    }

    @Test fun clearingThePlayerCancelsStorageRetry() = runTest(dispatcher) {
        val repository = FakePlayerAppearanceRepository().apply { failRead = true }
        val model = PlayerAppearanceViewModel(repository).also { store.put("player", it) }
        runCurrent(); assertEquals(PlayerAppearance(), model.appearance.value)
        store.clear(); advanceTimeBy(10000); runCurrent()
        assertEquals(1, repository.reads)
    }
}