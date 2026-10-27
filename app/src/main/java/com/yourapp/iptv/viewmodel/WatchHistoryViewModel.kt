package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.profile.ProfileScope

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.di.DefaultDispatcher
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.ui.state.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class WatchHistoryViewModel @Inject constructor(private val settings: SettingsRepository,
    private val history: WatchHistoryRepository, private val channels: ChannelRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow(WatchHistoryUiState())
    val uiState = state.asStateFlow()
    private var observer: Job? = null
    private var historyJob: Job? = null
    private var lookupJob: Job? = null
    private var generation = 0L
    private var lookupGeneration = 0L

    init { reload() }

    fun resume() { if (observer?.isActive != true) reload() }

    fun pause() {
        observer?.cancel(); historyJob?.cancel(); lookupJob?.cancel(); generation++
        state.update { it.copy(isSaving = false) }
    }

    fun reload() {
        if (state.value.isSaving) return
        observer?.cancel()
        historyJob?.cancel(); lookupJob?.cancel(); generation++
        state.update { it.copy(isLoading = true, error = null) }
        observer = viewModelScope.launch {
            try {
                settings.observeSettings().distinctUntilChangedBy(ProfileScope::of).collect { source ->
                    historyJob?.cancel(); lookupJob?.cancel()
                    val token = ++generation
                    val key = ProfileScope.of(source)
                    state.value = WatchHistoryUiState(sourceKey = key, isLoading = source.hasSelectedSource(),
                        needsSource = !source.hasSelectedSource())
                    if (!source.hasSelectedSource()) return@collect
                    historyJob = viewModelScope.launch {
                        try { history.observe(key).collect { records -> loadRows(source, token, records) } }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) {
                            if (generation == token) {
                                lookupJob?.cancel(); lookupGeneration++
                                state.update { it.copy(isLoading = false, error = WatchHistoryError.READ) }
                            }
                        }
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                historyJob?.cancel(); lookupJob?.cancel(); generation++
                state.value = WatchHistoryUiState(isLoading = false, error = WatchHistoryError.READ)
            }
        }
    }

    private fun loadRows(source: AppSettings, token: Long, records: List<WatchHistoryEntry>) {
        if (token != generation) return
        lookupJob?.cancel()
        val request = ++lookupGeneration
        lookupJob = viewModelScope.launch {
            try {
                val rows = withContext(background) {
                    val ids = records.mapTo(HashSet()) { it.channelId }
                    val available = if (ids.isEmpty()) emptyList() else channels.getPlaybackChannels(source, ids)
                    WatchHistory.project(records, available.associateBy { it.id })
                }
                currentCoroutineContext().ensureActive()
                if (generation == token && request == lookupGeneration)
                    state.update { it.copy(rows = rows, totalRecords = records.size, isLoading = false, error = null) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == token && request == lookupGeneration)
                    state.update { it.copy(isLoading = false, error = WatchHistoryError.CHANNELS) }
            }
        }
    }

    fun playbackChannel(source: String, id: String): Channel? = state.value.takeIf {
        it.sourceKey == source && !it.isLoading && !it.needsSource
    }?.rows?.firstOrNull { it.channel.id == id }?.channel

    fun remove(source: String, id: String) {
        if (state.value.rows.none { it.channel.id == id }) return
        edit(source) { history.remove(source, id) }
    }

    fun clear(source: String) = edit(source) { history.clear(source) }

    private fun edit(source: String, action: suspend () -> Unit) {
        if (source != state.value.sourceKey || source.isBlank() || state.value.isLoading ||
            state.value.isSaving || state.value.needsSource) return
        val token = generation
        state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == token) state.update { it.copy(error = WatchHistoryError.WRITE) }
            } finally {
                if (generation == token) state.update { it.copy(isSaving = false) }
            }
        }
    }
}