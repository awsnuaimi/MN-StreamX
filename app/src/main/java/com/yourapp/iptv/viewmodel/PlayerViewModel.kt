package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.di.DefaultDispatcher
import com.yourapp.iptv.domain.model.PlaybackQueue
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.ui.state.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val channels: ChannelRepository,
    @DefaultDispatcher private val background: CoroutineDispatcher
) : ViewModel() {
    private val state = MutableStateFlow(PlayerUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val id = savedStateHandle.get<String>("channelId")
                val group = savedStateHandle.get<String>("group")
                val query = savedStateHandle.get<String>("query").orEmpty()
                val available = channels.getChannels()
                val queue = withContext(background) { PlaybackQueue.create(available, group, query) }
                val index = queue.indexOf(id)
                state.value = PlayerUiState(
                    queue = queue,
                    currentIndex = index,
                    selectedGroup = group,
                    query = query,
                    isLoading = false,
                    errorMessage = if (index < 0) "القناة غير متاحة. اختر قناة من القائمة أو حدّث القنوات." else null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = PlayerUiState(isLoading = false, errorMessage = "تعذر فتح بيانات القناة.")
            }
        }
    }

    fun nextChannel() = moveChannel(1)

    fun previousChannel() = moveChannel(-1)

    fun selectChannel(id: String) {
        val snapshot = state.value
        val index = snapshot.queue.indexOf(id)
        if (index < 0 || index == snapshot.currentIndex) return
        savedStateHandle["channelId"] = id
        state.value = snapshot.copy(currentIndex = index, errorMessage = null)
    }

    private fun moveChannel(direction: Int) {
        val snapshot = state.value
        val index = snapshot.queue.adjacentIndex(snapshot.currentIndex, direction)
        snapshot.queue.channels.getOrNull(index)?.let { selectChannel(it.id) }
    }
}