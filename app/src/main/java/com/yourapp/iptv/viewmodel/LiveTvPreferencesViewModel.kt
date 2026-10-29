package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.LiveTvPreferences
import com.yourapp.iptv.domain.profile.ProfileScope
import com.yourapp.iptv.domain.repository.LiveTvPreferencesRepository
import com.yourapp.iptv.ui.state.LiveTvPreferencesState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LiveTvPreferencesViewModel @Inject constructor(private val repository: LiveTvPreferencesRepository) : ViewModel() {
    private val mutable = MutableStateFlow(LiveTvPreferencesState())
    val state = mutable.asStateFlow()
    private var read: Job? = null
    private var write: Job? = null
    private var generation = 0L
    fun bind(scope: String, retry: Boolean = false) {
        if (mutable.value.scope == scope && !retry) return
        val ticket = ++generation
        read?.cancel(); write?.cancel()
        mutable.value = LiveTvPreferencesState(scope)
        if (!ProfileScope.valid(scope)) return
        read = viewModelScope.launch {
            try {
                repository.observe(scope).collect { value ->
                    if (ticket == generation) mutable.update { it.copy(ready = true, value = value) }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (ticket == generation) mutable.update { it.copy(ready = false, error = true) } }
        }
    }
    fun change(scope: String, transform: (LiveTvPreferences) -> LiveTvPreferences) {
        val current = mutable.value
        if (current.scope != scope || !current.ready || current.saving) return
        val ticket = generation
        mutable.value = current.copy(saving = true, error = false)
        write = viewModelScope.launch {
            try { repository.update(scope, transform) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (ticket == generation) mutable.update { it.copy(error = true) } }
            finally { if (ticket == generation) mutable.update { it.copy(saving = false) } }
        }
    }
}