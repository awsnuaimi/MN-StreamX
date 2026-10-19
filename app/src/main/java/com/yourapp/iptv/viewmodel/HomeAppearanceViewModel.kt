package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.HomeAppearance
import com.yourapp.iptv.domain.repository.HomeAppearanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HomeAppearanceViewModel @Inject constructor(repository: HomeAppearanceRepository) : ViewModel() {
    private val state = MutableStateFlow<HomeAppearance?>(null)
    val appearance = state.asStateFlow()
    init { viewModelScope.launch {
        while (isActive) {
            try { repository.observe().collect { state.value = it.normalized() }; break }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (state.value == null) state.value = HomeAppearance(); delay(5000) }
        }
    } }
}

// A separate editor keeps unsaved choices out of the active interface.
data class HomeAppearanceEditorState(
    val value: HomeAppearance = HomeAppearance(), val loading: Boolean = true,
    val saving: Boolean = false, val failed: Boolean = false,
    val saved: Boolean = false, val error: String? = null
)

@HiltViewModel
class HomeAppearanceSettingsViewModel @Inject constructor(private val repository: HomeAppearanceRepository) : ViewModel() {
    private val state = MutableStateFlow(HomeAppearanceEditorState())
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    init { load() }
    fun load() {
        if (state.value.saving || loadJob?.isActive == true) return
        state.value = state.value.copy(loading = true, failed = false, error = null)
        loadJob = viewModelScope.launch {
            try { state.value = HomeAppearanceEditorState(repository.observe().first().normalized(), loading = false) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = state.value.copy(loading = false, failed = true, error = "تعذر قراءة مظهر الرئيسية.") }
        }
    }
    fun update(value: HomeAppearance) {
        if (state.value.loading || state.value.saving || state.value.failed) return
        state.value = state.value.copy(value = value.normalized(), error = null, saved = false)
    }
    fun reset() = update(HomeAppearance())
    fun save() {
        val current = state.value
        if (current.loading || current.saving || current.failed) return
        state.value = current.copy(saving = true, saved = false, error = null)
        viewModelScope.launch {
            try { repository.save(current.value.normalized()); state.value = state.value.copy(saving = false, saved = true) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.value = state.value.copy(saving = false, error = "تعذر الحفظ. أعد المحاولة.") }
        }
    }
}