package com.yourapp.iptv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.model.PlaylistLoadException
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.model.ChannelFavorites
import com.yourapp.iptv.domain.model.PlaybackQueue
import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.domain.repository.PlaylistRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.ui.state.HomeUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import com.yourapp.iptv.di.DefaultDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val playlists: PlaylistRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher,
    favorites: FavoriteRepository
) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState = state.asStateFlow()
    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var favoriteIds: Set<String>? = null
    private val favoriteController = FavoriteController(viewModelScope, favorites) {
        favoriteIds = it
        filterChannels()
    }
    val favoriteState = favoriteController.uiState

    fun retryFavorites() = favoriteController.retry()

    init { refresh() }

    fun refresh() {
        if (loadJob?.isActive == true) return
        state.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            try {
                val source = settings.observeSettings().first()
                if (!source.hasSelectedSource()) {
                    state.value = HomeUiState(isLoading = false, needsSource = true)
                    return@launch
                }
                val channels = when (source.source) {
                    PlaylistSource.M3U -> playlists.loadPlaylist(source.m3uUrl)
                    PlaylistSource.XTREAM -> playlists.loadXtream(
                        source.xtreamServer, source.xtreamUsername, source.xtreamPassword
                    )
                }.channels
                val groups = withContext(background) {
                    channels.mapNotNull { it.group }.distinct().sorted()
                }
                state.update {
                    it.copy(
                        channels = channels,
                        groups = groups,
                        selectedGroup = it.selectedGroup?.takeIf { group -> group in groups },
                        isLoading = false,
                        needsSource = false,
                        errorMessage = null
                    )
                }
                filterChannels()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = (error as? PlaylistLoadException)?.message
                            ?: "تعذر تحميل القنوات. تحقق من الشبكة والإعدادات ثم أعد المحاولة."
                    )
                }
            }
        }
    }

    fun updateQuery(query: String) {
        state.update { it.copy(query = query.take(200), selectedChannelId = null) }
        filterChannels()
    }

    fun selectGroup(group: String?) {
        state.update { it.copy(selectedGroup = group, selectedChannelId = null,
            favoritesOnly = if (group == null) false else it.favoritesOnly) }
        filterChannels()
    }

    fun toggleFavoritesFilter() {
        state.update { it.copy(favoritesOnly = !it.favoritesOnly, selectedChannelId = null) }
        filterChannels()
    }

    fun selectAllGroups() {
        state.update { it.copy(selectedGroup = null, selectedChannelId = null) }
        filterChannels()
    }

    fun selectSection(favoritesOnly: Boolean) {
        state.update { it.copy(favoritesOnly = favoritesOnly, selectedGroup = null, query = "", selectedChannelId = null) }
        filterChannels()
    }

    fun clearFilters() {
        state.update { it.copy(query = "", selectedGroup = null, favoritesOnly = false, selectedChannelId = null) }
        filterChannels()
    }

    fun selectChannel(id: String) {
        state.update { current ->
            val selected = current.visibleChannels.firstOrNull { it.id == id }?.id
                ?: current.visibleChannels.firstOrNull()?.id
            current.copy(selectedChannelId = selected)
        }
    }

    private fun filterChannels() {
        filterJob?.cancel()
        val snapshot = state.value
        val ids = favoriteIds
        filterJob = viewModelScope.launch {
            val (marked, visible) = withContext(background) {
                val marked = ChannelFavorites.apply(snapshot.channels, ids)
                marked to PlaybackQueue.create(marked, snapshot.selectedGroup, snapshot.query,
                    snapshot.favoritesOnly).channels
            }
            state.update { current ->
                current.copy(channels = marked, visibleChannels = visible,
                    selectedChannelId = current.selectedChannelId?.let { id ->
                        visible.firstOrNull { it.id == id }?.id ?: visible.firstOrNull()?.id
                    })
            }
        }
    }
}