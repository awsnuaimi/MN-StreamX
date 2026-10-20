package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.di.DefaultDispatcher
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.ChannelFavorites
import com.yourapp.iptv.domain.model.PlaybackQueue
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.ui.state.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val channels: ChannelRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher,
    favorites: FavoriteRepository,
    organization: OrganizationRepository = OrganizationRepository.Disabled
) : ViewModel() {
    private val state = MutableStateFlow(PlayerUiState(
        selectedGroup = savedStateHandle["group"],
        query = savedStateHandle.get<String>("query").orEmpty(),
        favoritesOnly = savedStateHandle.get<Boolean>("favoritesOnly") ?: false
    ))
    val uiState = state.asStateFlow()
    private var available: List<Channel>? = null
    private var favoriteIds: Set<String>? = null
    private var rebuildJob: Job? = null
    private var original = PlaybackQueue.empty()
    private val favoriteController = FavoriteController(viewModelScope, favorites) {
        favoriteIds = it
        rebuildQueue()
    }
    val favoriteState = favoriteController.uiState
    private val organizer = OrganizationController(viewModelScope, organization, background) { result ->
        available = result.channels
        val group = result.resolveFilter(state.value.selectedGroup)
        savedStateHandle["group"] = group
        state.update { it.copy(selectedGroup = group, organization = result.tree) }
        rebuildQueue()
    }

    init {
        viewModelScope.launch {
            try {
                organizer.setChannels(channels.getChannels())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = state.value.copy(isLoading = false, errorMessage = "تعذر فتح بيانات القناة.")
            }
        }
    }

    fun toggleFavorite() { state.value.channel?.let { favoriteController.toggle(it.id) } }

    fun retryFavorites() = favoriteController.retry()

    fun nextChannel() = moveChannel(1)

    fun previousChannel() = moveChannel(-1)

    fun selectChannel(id: String) {
        val snapshot = state.value
        val index = snapshot.queue.indexOf(id)
        if (index < 0 || (index == snapshot.currentIndex && snapshot.detachedChannel == null)) return
        savedStateHandle["channelId"] = id
        state.value = snapshot.copy(currentIndex = index, detachedChannel = null, errorMessage = null)
    }

    private fun moveChannel(direction: Int) {
        val snapshot = state.value
        if (snapshot.channel == null) return
        snapshot.queue.adjacentIdFrom(snapshot.channel?.id, direction, original)?.let(::selectChannel)
    }

    private fun rebuildQueue() {
        val stored = available ?: return
        rebuildJob?.cancel()
        val snapshot = state.value
        val ids = favoriteIds
        rebuildJob = viewModelScope.launch {
            val (base, queue) = withContext(background) {
                val marked = ChannelFavorites.apply(stored, ids)
                val base = PlaybackQueue.create(marked, snapshot.selectedGroup, snapshot.query)
                base to if (snapshot.favoritesOnly)
                    PlaybackQueue.create(marked, snapshot.selectedGroup, snapshot.query, true) else base
            }
            original = base
            // A DB emission must not undo zapping performed during background filtering.
            val id = savedStateHandle.get<String>("channelId")
            val index = queue.indexOf(id)
            val detached = if (snapshot.favoritesOnly && index < 0)
                base.channels.getOrNull(base.indexOf(id)) else null
            state.value = state.value.copy(queue = queue, currentIndex = index, detachedChannel = detached,
                isLoading = false,
                errorMessage = if (index < 0 && detached == null)
                    "القناة غير متاحة. اختر قناة من القائمة أو حدّث القنوات." else null)
        }
    }
}