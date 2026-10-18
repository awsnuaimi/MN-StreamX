package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.repository.ChannelRepository
import com.yourapp.iptv.ui.state.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channels: ChannelRepository
) : ViewModel() {
    private val state = MutableStateFlow(PlayerUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val id = savedStateHandle.get<String>("channelId")
                val channel = id?.let { channels.getChannel(it) }
                state.value = PlayerUiState(
                    channel = channel,
                    isLoading = false,
                    errorMessage = if (channel == null) "القناة غير متاحة. حدّث قائمة القنوات." else null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.value = PlayerUiState(isLoading = false, errorMessage = "تعذر فتح بيانات القناة.")
            }
        }
    }
}