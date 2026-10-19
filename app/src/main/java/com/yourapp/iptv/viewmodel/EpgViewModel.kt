package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.di.DefaultDispatcher
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.ChannelFavorites
import com.yourapp.iptv.domain.model.PlaybackQueue
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.ui.state.EpgUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val channels: ChannelRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher,
    favorites: FavoriteRepository
) : ViewModel() {
    private val state = MutableStateFlow(EpgUiState(
        selectedGroup = saved["group"], query = saved.get<String>("query").orEmpty(),
        favoritesOnly = saved.get<Boolean>("favoritesOnly") ?: false,
        selectedChannelId = saved["channelId"], selectedProgramKey = saved["epgProgramKey"]
    ))
    val uiState = state.asStateFlow()
    private var available = emptyList<Channel>()
    private var loaded = false
    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var favoriteIds: Set<String>? = null
    private val favoriteController = FavoriteController(viewModelScope, favorites) {
        favoriteIds = it
        filterChannels(showLoading = false)
    }
    val favoriteState = favoriteController.uiState

    fun toggleFavorite() { state.value.channel?.let { favoriteController.toggle(it.id) } }

    fun retryFavorites() = favoriteController.retry()

    init { reload() }

    fun reload() {
        if (loadJob?.isActive == true) return
        filterJob?.cancel()
        loaded = false
        state.update { it.copy(queue = PlaybackQueue.empty(), isLoading = true,
            isFiltering = false, errorMessage = null) }
        loadJob = viewModelScope.launch {
            try {
                val stored = channels.getChannels()
                val (unique, groups) = withContext(background) {
                    val unique = stored.distinctBy { it.id }
                    unique to unique.mapNotNull { it.group }.distinct().sorted()
                }
                available = unique
                loaded = true
                val group = state.value.selectedGroup?.takeIf { it in groups }
                saved["group"] = group
                state.update { it.copy(groups = groups, selectedGroup = group, isLoading = false) }
                filterChannels()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(isLoading = false, isFiltering = false,
                    errorMessage = "تعذر قراءة القنوات المحفوظة. أعد المحاولة أو حدّث القنوات من الصفحة الرئيسية.") }
            }
        }
    }

    fun updateQuery(query: String) {
        val value = query.take(200)
        if (state.value.query == value) return
        saved["query"] = value
        resetSelection()
        state.update { it.copy(query = value) }
        filterChannels()
    }

    fun selectGroup(group: String?) {
        if ((state.value.selectedGroup == group && (group != null || !state.value.favoritesOnly)) ||
            (group != null && group !in state.value.groups)) return
        saved["group"] = group
        if (group == null) saved["favoritesOnly"] = false
        resetSelection()
        state.update { it.copy(selectedGroup = group,
            favoritesOnly = if (group == null) false else it.favoritesOnly) }
        filterChannels()
    }

    fun toggleFavoritesFilter() {
        val value = !state.value.favoritesOnly
        saved["favoritesOnly"] = value
        resetSelection()
        state.update { it.copy(favoritesOnly = value) }
        filterChannels()
    }

    fun clearFilters() {
        saved["query"] = ""
        saved["group"] = null
        saved["favoritesOnly"] = false
        resetSelection()
        state.update { it.copy(query = "", selectedGroup = null, favoritesOnly = false) }
        filterChannels()
    }

    fun selectChannel(id: String) {
        val snapshot = state.value
        if (id == snapshot.selectedChannelId || snapshot.queue.indexOf(id) < 0) return
        saved["channelId"] = id
        saved["epgProgramKey"] = null
        state.update { it.copy(selectedChannelId = id, selectedProgramKey = null) }
    }

    fun restoreChannel(id: String) {
        if (id == state.value.selectedChannelId) return
        if (state.value.isLoading || state.value.isFiltering) {
            saved["channelId"] = id
            saved["epgProgramKey"] = null
            state.update { it.copy(selectedChannelId = id, selectedProgramKey = null) }
        } else selectChannel(id)
    }

    fun selectProgram(key: String) {
        if (key.length > 64 || state.value.channel == null || state.value.selectedProgramKey == key) return
        saved["epgProgramKey"] = key
        state.update { it.copy(selectedProgramKey = key) }
    }

    private fun resetSelection() {
        saved["channelId"] = null
        saved["epgProgramKey"] = null
        state.update { it.copy(queue = PlaybackQueue.empty(), selectedChannelId = null, selectedProgramKey = null) }
    }

    private fun filterChannels(showLoading: Boolean = true) {
        filterJob?.cancel()
        if (!loaded) return
        val snapshot = state.value
        val ids = favoriteIds
        if (showLoading) state.update { it.copy(isFiltering = true) }
        filterJob = viewModelScope.launch {
            val queue = withContext(background) {
                PlaybackQueue.create(ChannelFavorites.apply(available, ids), snapshot.selectedGroup,
                    snapshot.query, snapshot.favoritesOnly)
            }
            val requested = saved.get<String>("channelId")
            val selected = queue.channels.getOrNull(queue.indexOf(requested))?.id ?: queue.channels.firstOrNull()?.id
            if (selected != requested) saved["epgProgramKey"] = null
            saved["channelId"] = selected
            state.update { it.copy(queue = queue, selectedChannelId = selected,
                selectedProgramKey = saved["epgProgramKey"], isFiltering = false) }
        }
    }
}