package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.repository.StartupPlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StartupPlaybackSettingsState(
    val enabled: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val readFailed: Boolean = false,
    val saveFailed: Boolean = false
)

@HiltViewModel
class StartupPlaybackSettingsViewModel @Inject constructor(
    private val repository: StartupPlaybackRepository
) : ViewModel() {
    private val state = MutableStateFlow(StartupPlaybackSettingsState())
    val uiState = state.asStateFlow()
    private var work: Job? = null

    init { load() }

    fun load() {
        if (work?.isActive == true) return
        state.value = state.value.copy(isLoading = true, readFailed = false, saveFailed = false)
        work = viewModelScope.launch {
            try { state.value = StartupPlaybackSettingsState(enabled = repository.observe().first(), isLoading = false) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = state.value.copy(isLoading = false, readFailed = true) }
        }
    }

    /** Writes immediately; source settings and their import/save callback are untouched. */
    fun toggle() {
        val current = state.value
        if (current.isLoading || current.readFailed || current.isSaving || work?.isActive == true) return
        state.value = current.copy(isSaving = true, saveFailed = false)
        work = viewModelScope.launch {
            try {
                repository.save(!current.enabled)
                state.value = current.copy(enabled = !current.enabled, isSaving = false, saveFailed = false)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = current.copy(isSaving = false, saveFailed = true) }
        }
    }
}