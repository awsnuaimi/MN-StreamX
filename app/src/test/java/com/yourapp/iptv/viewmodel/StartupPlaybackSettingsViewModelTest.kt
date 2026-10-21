package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupPlaybackSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model(repository: StartupPreference) = StartupPlaybackSettingsViewModel(repository)
        .also { store.put("settings", it) }

    @Test fun cannotToggleBeforeReadingSavedPreference() = runTest(dispatcher) {
        val repository = StartupPreference(true).apply { readGate = CompletableDeferred() }
        val model = model(repository); model.toggle(); runCurrent()
        assertTrue(model.uiState.value.isLoading); assertEquals(0, repository.writes)
        repository.readGate!!.complete(Unit); runCurrent()
        assertTrue(model.uiState.value.enabled); assertFalse(model.uiState.value.isLoading)
    }

    @Test fun toggleSavesImmediatelyAndReopeningReadsTheNewValue() = runTest(dispatcher) {
        val repository = StartupPreference(false)
        val model = model(repository); runCurrent(); model.toggle(); runCurrent()
        assertTrue(repository.value.value); assertTrue(model.uiState.value.enabled)
        assertEquals(1, repository.writes)
        val reopened = model(repository); runCurrent(); assertTrue(reopened.uiState.value.enabled)
        reopened.toggle(); runCurrent(); assertFalse(repository.value.value)
    }

    @Test fun repeatedPressesCannotLaunchConcurrentOrDuplicateWrites() = runTest(dispatcher) {
        val repository = StartupPreference(false).apply { writeGate = CompletableDeferred() }
        val model = model(repository); runCurrent(); model.toggle(); model.toggle(); runCurrent()
        model.toggle(); assertEquals(1, repository.writes); assertTrue(model.uiState.value.isSaving)
        repository.writeGate!!.complete(Unit); runCurrent()
        assertTrue(model.uiState.value.enabled); assertFalse(model.uiState.value.isSaving)
    }

    @Test fun readFailureProtectsSavedPreferenceUntilSuccessfulRetry() = runTest(dispatcher) {
        val repository = StartupPreference(true).apply { failRead = true }
        val model = model(repository); runCurrent(); model.toggle(); runCurrent()
        assertTrue(model.uiState.value.readFailed); assertEquals(0, repository.writes)
        repository.failRead = false; model.load(); runCurrent()
        assertTrue(model.uiState.value.enabled); assertFalse(model.uiState.value.readFailed)
    }

    @Test fun failedSaveKeepsThePreviousValueAndCanBeRetried() = runTest(dispatcher) {
        val repository = StartupPreference(false).apply { failWrite = true }
        val model = model(repository); runCurrent(); model.toggle(); runCurrent()
        assertFalse(model.uiState.value.enabled); assertFalse(repository.value.value)
        assertTrue(model.uiState.value.saveFailed); assertFalse(model.uiState.value.isSaving)
        repository.failWrite = false; model.toggle(); runCurrent()
        assertTrue(model.uiState.value.enabled); assertFalse(model.uiState.value.saveFailed)
    }

    @Test fun repeatedReadRetriesNeverOverlap() = runTest(dispatcher) {
        val repository = StartupPreference(false).apply { readGate = CompletableDeferred() }
        val model = model(repository); model.load(); model.load(); runCurrent()
        assertEquals(1, repository.reads)
        repository.readGate!!.complete(Unit); runCurrent()
    }
}