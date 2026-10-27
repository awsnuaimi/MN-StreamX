package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SubscriptionRepository
import com.yourapp.iptv.domain.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSecureStorageTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private class Accounts : SubscriptionRepository {
        var failure: SecureStorageFailure? = null
        var writes = 0
        val catalog = Subscriptions(listOf(Subscription(Subscriptions.DEFAULT_ID,"Home",
            AppSettings(m3uUrl = "https://synthetic.invalid/list"))),Subscriptions.DEFAULT_ID)
        private fun checkFailure() { failure?.let { throw SecureStorageException(it) } }
        override fun observeSubscriptions() = flow { checkFailure();emit(catalog) }
        override fun observeSettings() = observeSubscriptions().map { it.active.settings }
        override suspend fun saveSettings(settings: AppSettings) { checkFailure();writes++ }
        override suspend fun saveSubscription(id: String?,name: String,settings: AppSettings): Subscriptions {
            saveSettings(settings);return catalog
        }
        override suspend fun activateSubscription(id: String): Subscriptions { checkFailure();return catalog }
        override suspend fun removeSubscription(id: String): Subscriptions { checkFailure();return catalog }
    }
    @Test fun everyStorageDiagnosisReachesTheUiAndRetryClearsItOnlyAfterSuccessfulRead() = runTest(dispatcher) {
        for (problem in SecureStorageFailure.entries) {
            val repository = Accounts().apply { failure = problem };val model = SettingsViewModel(repository)
            advanceUntilIdle()
            assertEquals(problem,model.uiState.value.storageFailure);assertFalse(model.uiState.value.enabled)
            model.saveSettings();assertEquals(0,repository.writes)
            repository.failure = null;model.loadSavedSettings();advanceUntilIdle()
            assertNull(model.uiState.value.storageFailure);assertTrue(model.uiState.value.enabled)
        }
    }
    @Test fun keyLossWhileEditingDisablesFurtherWritesAndExposesReload() = runTest(dispatcher) {
        val repository = Accounts();val model = SettingsViewModel(repository);advanceUntilIdle()
        repository.failure = SecureStorageFailure.KEY_MISSING
        model.updateM3uUrl("https://synthetic.invalid/edited");model.saveSettings();advanceUntilIdle()
        assertEquals(SecureStorageFailure.KEY_MISSING,model.uiState.value.storageFailure)
        assertTrue(model.uiState.value.loadFailed);assertFalse(model.uiState.value.enabled)
        model.saveSettings();assertEquals(0,repository.writes)
    }
}