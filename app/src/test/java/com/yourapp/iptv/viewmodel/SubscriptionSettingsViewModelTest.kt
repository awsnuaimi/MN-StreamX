package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModelStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.testing.FakeSubscriptions
import com.yourapp.iptv.ui.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }
    private fun model(repository: FakeSubscriptions) = SettingsViewModel(repository).also { owner.put("settings", it) }

    @Test fun addingAnAccountDoesNotSwitchOrSaveUntilOneValidatedSave() = runTest(dispatcher) {
        val repository = FakeSubscriptions(); val original = repository.catalog.value.active.settings
        val model = model(repository); runCurrent()
        model.request(SubscriptionAction.New)
        model.updateSubscriptionName("Second"); model.updateM3uUrl(FakeSubscriptions.account("B").m3uUrl)
        assertEquals(original, repository.catalog.value.active.settings); assertEquals(0, repository.saves)
        model.saveSettings(); model.saveSettings(); advanceTimeBy(21); runCurrent()
        assertEquals(1, repository.saves); assertEquals(2, repository.catalog.value.entries.size)
        assertEquals("Second", repository.catalog.value.active.name)
        assertTrue(model.uiState.value.isSaved)
    }

    @Test fun switchingAndEditingHaveDistinctEffectsAndRenamingDoesNotChangeSourceIdentity() = runTest(dispatcher) {
        val repository = FakeSubscriptions()
        repository.saveSubscription(null, "Second", FakeSubscriptions.account("B"))
        val second = repository.catalog.value.active
        val model = model(repository); runCurrent()
        model.request(SubscriptionAction.Edit(Subscriptions.DEFAULT_ID))
        assertEquals(second.id, repository.catalog.value.activeId)
        val before = PlaylistCacheKey.of(model.uiState.value.settings)
        model.updateSubscriptionName("Renamed")
        model.saveSettings(); advanceTimeBy(21); runCurrent()
        assertEquals(Subscriptions.DEFAULT_ID, repository.catalog.value.activeId)
        assertEquals(before, PlaylistCacheKey.of(repository.catalog.value.active.settings))
        assertEquals("Renamed", repository.catalog.value.active.name)
    }

    @Test fun dirtyDraftNeedsExplicitConfirmationAndCancellingKeepsItsCredentials() = runTest(dispatcher) {
        val repository = FakeSubscriptions()
        repository.saveSubscription(null, "Second", FakeSubscriptions.account("B"))
        val model = model(repository); runCurrent()
        model.updateM3uUrl("https://synthetic.invalid/draft.m3u")
        model.request(SubscriptionAction.Activate(Subscriptions.DEFAULT_ID))
        assertNotNull(model.uiState.value.pending)
        assertNotEquals(Subscriptions.DEFAULT_ID, repository.catalog.value.activeId)
        model.dismissAction(); assertTrue(model.uiState.value.dirty)
        assertTrue(model.uiState.value.settings.m3uUrl.endsWith("draft.m3u"))
        model.request(SubscriptionAction.Activate(Subscriptions.DEFAULT_ID)); model.confirmAction()
        advanceTimeBy(21); runCurrent()
        assertEquals(Subscriptions.DEFAULT_ID, repository.catalog.value.activeId)
        assertFalse(model.uiState.value.dirty)
    }

    @Test fun deletingAnInactiveAccountIsConfirmedAndDoesNotChangeTheActiveAccountOrUnrelatedDraft() = runTest(dispatcher) {
        val repository = FakeSubscriptions()
        repository.saveSubscription(null, "Second", FakeSubscriptions.account("B"))
        val active = repository.catalog.value.activeId
        val model = model(repository); runCurrent(); model.updateSubscriptionName("Unfinished rename")
        model.request(SubscriptionAction.Remove(Subscriptions.DEFAULT_ID)); runCurrent()
        assertEquals(2, repository.catalog.value.entries.size)
        model.confirmAction(); advanceTimeBy(21); runCurrent()
        assertEquals(active, repository.catalog.value.activeId)
        assertEquals(1, repository.catalog.value.entries.size)
        assertEquals("Unfinished rename", model.uiState.value.subscriptionName)
        assertTrue(model.uiState.value.dirty)
    }

    @Test fun duplicateAndUnnamedAccountsNeverReplaceTheWorkingActiveAccount() = runTest(dispatcher) {
        val repository = FakeSubscriptions(); val original = repository.catalog.value
        val model = model(repository); runCurrent()
        model.request(SubscriptionAction.New)
        model.updateM3uUrl(original.active.settings.m3uUrl)
        model.saveSettings(); advanceTimeBy(21); runCurrent()
        assertEquals(SettingsIssue.SUBSCRIPTION_NAME, model.uiState.value.issue)
        model.updateSubscriptionName("Duplicate"); model.saveSettings(); advanceTimeBy(21); runCurrent()
        assertEquals(SettingsIssue.SUBSCRIPTION_DUPLICATE, model.uiState.value.issue)
        assertEquals(original, repository.catalog.value)
    }

    @Test fun aFailedSwitchKeepsTheOriginalAccountAndAllowsRetry() = runTest(dispatcher) {
        val repository = FakeSubscriptions()
        repository.saveSubscription(null, "Second", FakeSubscriptions.account("B"))
        val active = repository.catalog.value.activeId
        val model = model(repository); runCurrent(); repository.failWrite = true
        model.request(SubscriptionAction.Activate(Subscriptions.DEFAULT_ID)); advanceTimeBy(21); runCurrent()
        assertEquals(active, repository.catalog.value.activeId)
        assertEquals(SettingsIssue.SAVE_FAILED, model.uiState.value.issue)
        repository.failWrite = false
        model.request(SubscriptionAction.Activate(Subscriptions.DEFAULT_ID)); advanceTimeBy(21); runCurrent()
        assertEquals(Subscriptions.DEFAULT_ID, repository.catalog.value.activeId)
    }
    @Test fun anAlreadyCommittedSaveDoesNotBecomeAFailureBecauseOfAnUnnecessarySecondRead() = runTest(dispatcher) {
        val repository = FakeSubscriptions()
        val model = model(repository); runCurrent()
        repository.failReadAfterSave = true
        model.updateSubscriptionName("Renamed")
        model.saveSettings(); advanceTimeBy(21); runCurrent()
        assertEquals("Renamed", repository.catalog.value.active.name)
        assertTrue(model.uiState.value.isSaved); assertNull(model.uiState.value.issue)
        assertEquals("Renamed", model.uiState.value.subscriptionName)
    }

}