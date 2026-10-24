package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.repository.WatchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Reuses confirmed playback history; never loads channels, posters, or VOD catalogs. */
data class HomeQuickAccessState(val sourceKey: String? = null, val channelIds: List<String> = emptyList())

@HiltViewModel
class HomeQuickAccessViewModel @Inject constructor(
    private val history: WatchHistoryRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeQuickAccessState())
    val uiState = mutableState.asStateFlow()
    private var observing: Job? = null
    private var currentKey: String? = null

    fun bind(sourceKey: String?) {
        val key = sourceKey?.takeIf { it.length == 64 && it.all { char -> char in '0'..'9' || char in 'a'..'f' } }
        if (currentKey == key) return
        observing?.cancel()
        currentKey = key
        mutableState.value = HomeQuickAccessState(sourceKey = key)
        if (key == null) return
        observing = viewModelScope.launch {
            try {
                history.observe(key).collect { entries ->
                    if (currentKey == key) {
                        mutableState.value = HomeQuickAccessState(key,
                            entries.asSequence().map { it.channelId }.distinct().take(8).toList())
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // History is optional: a corrupt history never blocks the main catalog.
                if (currentKey == key) mutableState.value = HomeQuickAccessState(key)
            }
        }
    }

    override fun onCleared() {
        observing?.cancel()
        super.onCleared()
    }
}