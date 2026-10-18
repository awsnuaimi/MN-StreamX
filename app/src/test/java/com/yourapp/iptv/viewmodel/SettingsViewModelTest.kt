package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun saveIsAtomicAndDuplicateClicksAreIgnored() = runTest(dispatcher) {
        val repository = FakeSettings()
        val model = SettingsViewModel(repository)
        advanceUntilIdle()
        model.updateM3uUrl(" https://unit.test/list.m3u ")
        model.updateEpgUrl("https://unit.test/epg.xml")
        model.saveSettings()
        model.saveSettings()
        advanceUntilIdle()
        assertEquals(1, repository.saves)
        assertEquals("https://unit.test/list.m3u", repository.value.m3uUrl)
        assertEquals("https://unit.test/epg.xml", repository.value.epgUrl)
        assertTrue(model.uiState.value.isSaved)
        assertFalse(model.uiState.value.isSaving)
    }

    @Test fun invalidUrlDoesNotWriteAnything() = runTest(dispatcher) {
        val repository = FakeSettings()
        val model = SettingsViewModel(repository)
        advanceUntilIdle()
        model.updateM3uUrl("https://")
        model.saveSettings()
        advanceUntilIdle()
        assertEquals(0, repository.saves)
        assertNotNull(model.uiState.value.errorMessage)
    }

    @Test fun editingDuringInitialReadCannotOverwriteStoredSettings() = runTest(dispatcher) {
        val repository = FakeSettings(AppSettings(m3uUrl = "https://unit.test/stored.m3u"))
        val model = SettingsViewModel(repository)
        model.updateM3uUrl("https://unit.test/early-edit.m3u")
        advanceUntilIdle()
        assertEquals(repository.value.m3uUrl, model.uiState.value.settings.m3uUrl)
    }

    @Test fun readFailureDisablesSavingUntilSuccessfulRetry() = runTest(dispatcher) {
        val repository = FakeSettings().apply { failRead = true }
        val model = SettingsViewModel(repository)
        advanceUntilIdle()
        model.saveSettings()
        assertTrue(model.uiState.value.loadFailed)
        assertEquals(0, repository.saves)
        repository.failRead = false
        model.loadSavedSettings()
        advanceUntilIdle()
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test fun writeFailureKeepsFormAndReportsFailure() = runTest(dispatcher) {
        val repository = FakeSettings().apply { failSave = true }
        val model = SettingsViewModel(repository)
        advanceUntilIdle()
        model.updateM3uUrl("https://unit.test/list.m3u")
        model.saveSettings()
        advanceUntilIdle()
        assertFalse(model.uiState.value.isSaving)
        assertFalse(model.uiState.value.isSaved)
        assertNotNull(model.uiState.value.errorMessage)
        assertEquals("https://unit.test/list.m3u", model.uiState.value.settings.m3uUrl)
    }

    @Test fun xtreamSavesWithoutM3uAndPreservesPasswordExactly() = runTest(dispatcher) {
        val repository = FakeSettings()
        val model = SettingsViewModel(repository)
        advanceUntilIdle()
        model.updateSource(PlaylistSource.XTREAM)
        model.updateM3uUrl("invalid-unused-link")
        model.updateXtreamServer(" https://unit.test:8080 ")
        model.updateXtreamUsername(" user ")
        model.updateXtreamPassword(" p&/+# ")
        model.saveSettings()
        advanceUntilIdle()
        assertEquals(1, repository.saves)
        assertEquals(PlaylistSource.XTREAM, repository.value.source)
        assertEquals("user", repository.value.xtreamUsername)
        assertEquals(" p&/+# ", repository.value.xtreamPassword)
        assertTrue(model.uiState.value.isSaved)
    }

    @Test fun incompleteXtreamCannotSaveAndUnusedXtreamDoesNotBlockM3u() = runTest(dispatcher) {
        val repository = FakeSettings()
        val model = SettingsViewModel(repository)
        advanceUntilIdle()
        model.updateSource(PlaylistSource.XTREAM)
        model.updateXtreamServer("https://unit.test")
        model.saveSettings()
        advanceUntilIdle()
        assertEquals(0, repository.saves)
        model.updateSource(PlaylistSource.M3U)
        model.updateM3uUrl("https://unit.test/list.m3u")
        model.saveSettings()
        advanceUntilIdle()
        assertEquals(1, repository.saves)
    }

    private class FakeSettings(var value: AppSettings = AppSettings()) : SettingsRepository {
        var saves = 0
        var failRead = false
        var failSave = false
        override fun observeSettings(): Flow<AppSettings> = flow {
            delay(50)
            if (failRead) throw IOException("Synthetic read failure")
            emit(value)
        }
        override suspend fun saveSettings(settings: AppSettings) {
            delay(50)
            if (failSave) throw IOException("Synthetic write failure")
            value = settings
            saves++
        }
    }
}