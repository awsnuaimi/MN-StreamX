package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerAppearanceSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model(repository: FakePlayerAppearanceRepository) =
        PlayerAppearanceSettingsViewModel(repository).also { store.put("appearance", it) }

    @Test fun editingBeforeReadingCannotOverwriteSavedAppearance() = runTest(dispatcher) {
        val saved = PlayerAppearance(style = PlayerInfoStyle.CLASSIC)
        val repository = FakePlayerAppearanceRepository(saved).apply { latency = 100 }
        val model = model(repository)
        model.update(PlayerAppearance(textSize = PlayerTextSize.LARGE)); model.save()
        advanceUntilIdle()
        assertEquals(saved, model.uiState.value.appearance)
        assertEquals(0, repository.saves)
    }

    @Test fun saveIsAtomicAndRepeatedClicksCannotDuplicateWrites() = runTest(dispatcher) {
        val repository = FakePlayerAppearanceRepository().apply { latency = 100 }
        val model = model(repository); advanceUntilIdle()
        val value = PlayerAppearance(style = PlayerInfoStyle.CLASSIC, textSize = PlayerTextSize.LARGE)
        model.update(value); model.save(); model.save()
        model.update(PlayerAppearance()); advanceUntilIdle()
        assertEquals(1, repository.saves)
        assertEquals(value, repository.values.value)
        assertTrue(model.uiState.value.isSaved)
    }

    @Test fun resetIsADraftUntilTheUserSavesIt() = runTest(dispatcher) {
        val repository = FakePlayerAppearanceRepository(PlayerAppearance(showHints = true))
        val model = model(repository); advanceUntilIdle(); model.reset()
        assertEquals(PlayerAppearance(), model.uiState.value.appearance)
        assertTrue(repository.values.value.showHints)
        assertEquals(0, repository.saves)
        model.save(); advanceUntilIdle()
        assertEquals(PlayerAppearance(), repository.values.value)
    }

    @Test fun failedReadBlocksWritingUntilSuccessfulRetry() = runTest(dispatcher) {
        val repository = FakePlayerAppearanceRepository().apply { failRead = true }
        val model = model(repository); advanceUntilIdle(); model.save()
        assertTrue(model.uiState.value.loadFailed)
        assertEquals(0, repository.saves)
        repository.failRead = false; model.load(); advanceUntilIdle()
        assertFalse(model.uiState.value.loadFailed)
        model.save(); advanceUntilIdle(); assertEquals(1, repository.saves)
    }

    @Test fun failedWriteRetainsChoicesAndCanBeRetried() = runTest(dispatcher) {
        val repository = FakePlayerAppearanceRepository().apply { failSave = true }
        val model = model(repository); advanceUntilIdle()
        val value = PlayerAppearance(panelShade = PlayerPanelShade.SOLID, displaySeconds = 8)
        model.update(value); model.save(); advanceUntilIdle()
        assertFalse(model.uiState.value.isSaving)
        assertNotNull(model.uiState.value.errorMessage)
        assertEquals(value, model.uiState.value.appearance)
        repository.failSave = false; model.save(); advanceUntilIdle()
        assertEquals(value, repository.values.value)
        assertTrue(model.uiState.value.isSaved)
    }

    @Test fun loadRetriesCannotRunConcurrently() = runTest(dispatcher) {
        val repository = FakePlayerAppearanceRepository().apply { latency = 100 }
        val model = model(repository); model.load(); model.load(); advanceUntilIdle()
        assertEquals(1, repository.reads)
    }
}