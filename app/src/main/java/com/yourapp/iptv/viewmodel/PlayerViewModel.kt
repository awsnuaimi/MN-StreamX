package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import com.yourapp.iptv.domain.repository.WatchHistoryRepository
import com.yourapp.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.di.DefaultDispatcher
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.ChannelFavorites
import com.yourapp.iptv.domain.model.PlaybackQueue
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.domain.repository.FavoriteRepository
import com.yourapp.iptv.ui.state.PlayerUiState
import com.yourapp.iptv.util.LoadTimings
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
    organization: OrganizationRepository = OrganizationRepository.Disabled,
    history: WatchHistoryRepository = WatchHistoryRepository.Disabled,
    private val settings: SettingsRepository? = null
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
    private val historyRecorder = PlaybackHistoryRecorder(history) { state.value.channel }
    private val lastChannel = LastChannelController(viewModelScope, history, channels, settings) { state.value.channel }
    val lastChannelState = lastChannel.uiState
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
                val source = try { settings?.observeSettings()?.first() }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
                historyRecorder.bind(source)
                lastChannel.bind(historyRecorder.sourceKey)
                val expected = savedStateHandle.get<String>("sourceKey")
                if (expected != null && expected != historyRecorder.sourceKey) {
                    state.update { it.copy(isLoading = false, sourceChanged = true) }
                    return@launch
                }
                val id = savedStateHandle.get<String>("channelId")
                val selected = try {
                    if (id == null) null else LoadTimings.measure(LoadTimings.Stage.CHANNEL_LOOKUP) {
                        channels.getPlaybackChannel(id)
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { null } // Full-list recovery remains available.
                if (selected != null && selected.id == id) {
                    val marked = favoriteIds?.let { selected.copy(isFavorite = selected.id in it) } ?: selected
                    state.update { it.copy(detachedChannel = marked, isLoading = false, errorMessage = null) }
                }
                organizer.setChannels(channels.getChannels())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(isLoading = false,
                    errorMessage = if (it.channel == null) "تعذر فتح بيانات القناة." else null) }
            }
        }
    }

    fun toggleFavorite() { state.value.channel?.let { favoriteController.toggle(it.id) } }

    fun retryFavorites() = favoriteController.retry()

    fun onPlaybackStarted(channel: Channel) = historyRecorder.onFirstFrame(channel)

    fun showLastChannelMenu() = lastChannel.start()
    fun hideLastChannelMenu() = lastChannel.stop()
    fun retryLastChannel() = lastChannel.retry()
    suspend fun recallLastChannel() = lastChannel.recall()

    /** Reuse the running player and cached full queue when the selected metadata still matches. */
    fun selectHistoryChannel(selected: Channel, sourceKey: String): Boolean {
        if (historyRecorder.sourceKey != sourceKey) return false
        val snapshot = state.value
        val index = snapshot.queue.indexOf(selected.id)
        val present = snapshot.queue.channels.getOrNull(index) ?: available?.firstOrNull { it.id == selected.id } ?: return false
        if (present.streamUrl != selected.streamUrl) return false
        if (state.value.channel?.id == present.id && state.value.channel?.streamUrl == present.streamUrl) return true
        if (index >= 0) { selectChannel(present.id); return true }
        savedStateHandle["channelId"] = present.id
        savedStateHandle["group"] = null
        savedStateHandle["query"] = ""
        savedStateHandle["favoritesOnly"] = false
        state.update { it.copy(selectedGroup = null, query = "", favoritesOnly = false, errorMessage = null) }
        rebuildQueue()
        return true
    }

    fun nextChannel() = moveChannel(1)

    fun previousChannel() = moveChannel(-1)

    fun selectChannel(id: String) {
        val snapshot = state.value
        val index = snapshot.queue.indexOf(id)
        if (index < 0 || (index == snapshot.currentIndex && snapshot.detachedChannel == null)) return
        savedStateHandle["channelId"] = id
        state.value = snapshot.copy(currentIndex = index, detachedChannel = null, errorMessage = null)
        lastChannel.onSelectionChanged()
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
            lastChannel.onSelectionChanged()
        }
    }
}