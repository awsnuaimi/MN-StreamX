package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.HomeAppearanceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAppearanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private class Repository : HomeAppearanceRepository {
        val values = MutableStateFlow(HomeAppearance())
        var failRead = false; var failWrite = false; var writes = 0
        override fun observe() = flow { if (failRead) throw IOException(); emitAll(values) }
        override suspend fun save(value: HomeAppearance) {
            writes++; delay(20); if (failWrite) throw IOException(); values.value = value
        }
    }
    @Test fun draftDoesNotChangeActiveHomeUntilOneSuccessfulSave() = runTest(dispatcher) {
        val repository = Repository()
        val reader = HomeAppearanceViewModel(repository).also { store.put("reader", it) }
        val editor = HomeAppearanceSettingsViewModel(repository).also { store.put("editor", it) }
        assertNull(reader.appearance.value)
        runCurrent()
        val choice = HomeAppearance(HomeLanguage.ENGLISH, SidebarSide.LEFT, true, 4)
        editor.update(choice); runCurrent()
        assertEquals(HomeAppearance(), reader.appearance.value)
        editor.save(); editor.save(); advanceTimeBy(21); runCurrent()
        assertEquals(1, repository.writes)
        assertEquals(choice, reader.appearance.value)
        assertTrue(editor.uiState.value.saved)
    }
    @Test fun failedReadBlocksOverwriteUntilExplicitReloadSucceeds() = runTest(dispatcher) {
        val repository = Repository().apply { failRead = true }
        val editor = HomeAppearanceSettingsViewModel(repository).also { store.put("editor", it) }
        runCurrent(); editor.update(HomeAppearance(columns = 4)); editor.save(); runCurrent()
        assertTrue(editor.uiState.value.failed); assertEquals(0, repository.writes)
        repository.failRead = false; editor.load(); runCurrent()
        assertFalse(editor.uiState.value.failed); assertFalse(editor.uiState.value.loading)
    }
    @Test fun failedWritePreservesDraftAndRetrySavesIt() = runTest(dispatcher) {
        val repository = Repository().apply { failWrite = true }
        val editor = HomeAppearanceSettingsViewModel(repository).also { store.put("editor", it) }
        runCurrent(); editor.update(HomeAppearance(columns = 2)); editor.save()
        advanceTimeBy(21); runCurrent()
        assertNotNull(editor.uiState.value.error); assertEquals(2, editor.uiState.value.value.columns)
        assertFalse(editor.uiState.value.saved)
        repository.failWrite = false; editor.save(); advanceTimeBy(21); runCurrent()
        assertTrue(editor.uiState.value.saved); assertEquals(2, repository.values.value.columns)
    }
}