package com.yourapp.iptv.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.iptv.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class StartupPlaybackViewModel @Inject constructor(
    preference: StartupPlaybackRepository,
    settings: SettingsRepository,
    history: WatchHistoryRepository,
    channels: ChannelRepository,
    savedState: SavedStateHandle
) : ViewModel() {
    private val controller = StartupPlaybackController(viewModelScope, preference, settings, history, channels,
        savedState["startupPlaybackAttempted"] ?: false) { savedState["startupPlaybackAttempted"] = true }

    fun initialize(freshLaunch: Boolean) = controller.initialize(freshLaunch)
    fun cancel() = controller.cancel()
    fun onHomeReady(sourceKey: String, onPlay: (String, String) -> Unit) =
        controller.onHomeReady(sourceKey) { onPlay(it.channelId, it.sourceKey) }
}