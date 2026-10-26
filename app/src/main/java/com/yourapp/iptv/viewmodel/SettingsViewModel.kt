package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SubscriptionRepository
import com.yourapp.iptv.ui.state.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SubscriptionRepository) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState = state.asStateFlow()
    private var catalog: Subscriptions? = null
    private var originalSettings = AppSettings()
    private var originalName = ""
    private var loadJob: Job? = null
    init { loadSavedSettings() }

    fun loadSavedSettings() {
        if (state.value.isSaving || loadJob?.isActive == true) return
        state.update { it.copy(isLoading = true, loadFailed = false, issue = null, pending = null) }
        loadJob = viewModelScope.launch {
            try {
                catalog = repository.observeSubscriptions().first()
                editDraft(requireNotNull(catalog).active)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                state.update { it.copy(isLoading = false, loadFailed = true, issue = SettingsIssue.READ_FAILED) }
            }
        }
    }

    fun updateM3uUrl(value: String) = edit { copy(m3uUrl = value) }
    fun updateSource(value: PlaylistSource) = edit { copy(source = value) }
    fun updateXtreamServer(value: String) = edit { copy(xtreamServer = value) }
    fun updateXtreamUsername(value: String) = edit { copy(xtreamUsername = value) }
    fun updateXtreamPassword(value: String) = edit { copy(xtreamPassword = value) }
    fun updateEpgUrl(value: String) = edit { copy(epgUrl = value) }
    fun updateSubscriptionName(value: String) {
        if (!state.value.enabled) return
        val title = value.take(Subscriptions.NAME_LIMIT)
        state.update { it.copy(subscriptionName = title, dirty = title != originalName || it.settings != originalSettings,
            issue = null, isSaved = false) }
    }
    private fun edit(change: AppSettings.() -> AppSettings) {
        if (!state.value.enabled) return
        state.update {
            val changed = it.settings.change()
            it.copy(settings = changed, dirty = changed != originalSettings || it.subscriptionName != originalName,
                issue = null, isSaved = false)
        }
    }

    fun request(action: SubscriptionAction) {
        if (!state.value.enabled) return
        if (action is SubscriptionAction.Remove || state.value.dirty) {
            state.update { it.copy(pending = action) }
        } else execute(action)
    }
    fun dismissAction() { state.update { it.copy(pending = null) } }
    fun confirmAction() {
        val action = state.value.pending ?: return
        dismissAction(); execute(action)
    }
    private fun execute(action: SubscriptionAction) {
        if (!state.value.enabled) return
        val current = catalog ?: return
        when (action) {
            SubscriptionAction.New -> editDraft(null)
            is SubscriptionAction.Edit -> current.entries.firstOrNull { it.id == action.id }?.let(::editDraft)
            is SubscriptionAction.Activate -> operate(navigate = true) { repository.activateSubscription(action.id) }
            is SubscriptionAction.Remove -> operate(navigate = false) { repository.removeSubscription(action.id) }
        }
    }

    fun saveSettings() {
        val current = state.value
        if (!current.enabled) return
        val settings = current.settings.let { it.copy(m3uUrl = it.m3uUrl.trim(), xtreamServer = it.xtreamServer.trim(),
            xtreamUsername = it.xtreamUsername.trim(), epgUrl = it.epgUrl.trim()) }
        val error = SettingsValidation.validate(settings)
        if (error != null) { state.update { it.copy(issue = error, isSaved = false) }; return }
        operate(navigate = true) {
            repository.saveSubscription(current.editingSubscriptionId, current.subscriptionName, settings)
        }
    }
    private fun operate(navigate: Boolean, block: suspend () -> Subscriptions) {
        if (!state.value.enabled) return
        state.update { it.copy(isSaving = true, issue = null, isSaved = false, pending = null) }
        viewModelScope.launch {
            try {
                val latest = block()
                catalog = latest
                if (navigate || latest.entries.none { it.id == state.value.editingSubscriptionId }) {
                    editDraft(latest.active)
                } else state.update { it.copy(subscriptions = latest.entries, activeSubscriptionId = latest.activeId, isSaving = false) }
                if (navigate) state.update { it.copy(isSaved = true) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                state.update { it.copy(isSaving = false, issue = SettingsValidation.issue(error)) }
            }
        }
    }
    private fun editDraft(subscription: Subscription?) {
        val current = catalog ?: return
        originalSettings = subscription?.settings ?: AppSettings()
        originalName = subscription?.name.orEmpty()
        state.value = SettingsUiState(settings = originalSettings, isLoading = false,
            subscriptions = current.entries, activeSubscriptionId = current.activeId,
            editingSubscriptionId = subscription?.id, subscriptionName = originalName)
    }
    fun resetSaveStatus() { state.update { it.copy(isSaved = false) } }
}