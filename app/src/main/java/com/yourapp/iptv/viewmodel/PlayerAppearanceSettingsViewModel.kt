package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.repository.PlayerAppearanceRepository
import com.yourapp.iptv.ui.state.PlayerAppearanceUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerAppearanceSettingsViewModel @Inject constructor(
    private val repository: PlayerAppearanceRepository
) : ViewModel() {
    private val state = MutableStateFlow(PlayerAppearanceUiState())
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    init { load() }

    fun load() {
        if (state.value.isSaving || loadJob?.isActive == true) return
        state.update { it.copy(isLoading = true, loadFailed = false, errorMessage = null) }
        loadJob = viewModelScope.launch {
            try {
                state.value = PlayerAppearanceUiState(repository.observe().first().normalized(), isLoading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(isLoading = false, loadFailed = true, errorMessage = "تعذر قراءة مظهر المشغّل المحفوظ.") }
            }
        }
    }

    fun update(value: PlayerAppearance) {
        val current = state.value
        if (current.isLoading || current.isSaving || current.loadFailed) return
        state.update { it.copy(appearance = value.normalized(), errorMessage = null, isSaved = false) }
    }
    fun reset() = update(PlayerAppearance(profileId = state.value.appearance.profileId))
    fun resetSaveStatus() { state.update { it.copy(isSaved = false) } }

    fun save() {
        val current = state.value
        if (current.isLoading || current.isSaving || current.loadFailed) return
        val appearance = current.appearance.normalized()
        state.update { it.copy(isSaving = true, isSaved = false, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.save(appearance)
                state.update { it.copy(appearance = appearance, isSaving = false, isSaved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(isSaving = false, errorMessage = "تعذر حفظ المظهر. أعد المحاولة.") }
            }
        }
    }
}