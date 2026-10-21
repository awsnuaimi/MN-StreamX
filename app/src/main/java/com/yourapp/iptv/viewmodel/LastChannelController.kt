package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import com.yourapp.iptv.ui.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.coroutineContext

/** Reads history only while More is visible. Playback itself never waits for this controller. */
internal class LastChannelController(private val scope: CoroutineScope,
    private val history: WatchHistoryRepository, private val channels: ChannelRepository,
    private val settings: SettingsRepository?, private val current: () -> Channel?) {
    private val state = MutableStateFlow(LastChannelUiState())
    val uiState = state.asStateFlow()
    private var sourceKey: String? = null
    private var visible = false
    private var generation = 0L
    private var observation: Job? = null
    private var records = emptyList<WatchHistoryEntry>()

    fun bind(key: String?) {
        if (key == sourceKey) return
        val resume = visible
        stop(); sourceKey = key
        if (resume) start()
    }

    fun start() {
        if (visible) return
        visible = true
        val key = sourceKey ?: return
        val request = ++generation
        state.value = LastChannelUiState(isLoading = true)
        observation = scope.launch {
            try {
                history.observe(key).collect { rows ->
                    if (generation != request) return@collect
                    records = rows.distinctBy { it.channelId }.take(WatchHistory.LIMIT)
                    state.update { it.copy(targetId = candidate(), isLoading = false, error = null) }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == request) state.value = LastChannelUiState(error = LastChannelError.HISTORY)
            }
        }
    }

    fun stop() {
        visible = false; generation++
        observation?.cancel(); observation = null
        records = emptyList(); state.value = LastChannelUiState()
    }

    fun retry() { if (visible) { stop(); start() } }

    fun onSelectionChanged() {
        if (visible) state.update { it.copy(targetId = candidate()) }
    }

    suspend fun recall(): LastChannelTarget? {
        if (!visible || !state.value.canRecall || state.value.isSwitching) return null
        val key = sourceKey ?: return null
        val active = current() ?: return null
        val request = generation
        val ids = records.map { it.channelId }.filter { it != active.id }.toSet()
        if (ids.isEmpty()) return null
        state.update { it.copy(isSwitching = true, error = null) }
        try {
            val sourceSettings = settings ?: run { fail(request, LastChannelError.SOURCE_CHANGED); return null }
            val source = sourceSettings.observeSettings().first()
            if (!source.hasSelectedSource() || PlaylistCacheKey.of(source) != key) {
                fail(request, LastChannelError.SOURCE_CHANGED); return null
            }
            if (!isCurrent(request, key, active)) return null
            // One small, source-checked request; warm data comes from the existing repository cache.
            val found = channels.getPlaybackChannels(source, ids).associateBy { it.id }
            val latest = sourceSettings.observeSettings().first()
            if (!latest.hasSelectedSource() || PlaylistCacheKey.of(latest) != key) {
                fail(request, LastChannelError.SOURCE_CHANGED); return null
            }
            if (!isCurrent(request, key, active)) return null
            val selected = records.asSequence().map { it.channelId }.filter { it != active.id && it in ids }
                .mapNotNull { found[it] }.firstOrNull()
            if (selected == null) {
                state.update { it.copy(targetId = null, error = LastChannelError.UNAVAILABLE) }
                return null
            }
            return LastChannelTarget(selected, key)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { fail(request, LastChannelError.LOOKUP); return null }
        finally {
            if (generation == request) state.update { it.copy(isSwitching = false) }
        }
    }

    private fun candidate(): String? = records.firstOrNull { it.channelId != current()?.id }?.channelId

    private suspend fun isCurrent(request: Long, key: String, active: Channel): Boolean {
        coroutineContext.ensureActive()
        val selected = current()
        return visible && generation == request && sourceKey == key && selected != null && selected.id == active.id &&
            selected.streamUrl == active.streamUrl
    }

    private fun fail(request: Long, error: LastChannelError) {
        if (generation == request) state.update { it.copy(error = error) }
    }
}