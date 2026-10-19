package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlaybackQueue

data class PlayerUiState(
    val queue: PlaybackQueue = PlaybackQueue.empty(),
    val currentIndex: Int = -1,
    val selectedGroup: String? = null,
    val favoritesOnly: Boolean = false,
    val detachedChannel: Channel? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val channel: Channel? get() = queue.channels.getOrNull(currentIndex) ?: detachedChannel
    val canChangeChannel: Boolean get() = channel != null &&
        (queue.size > 1 || (queue.size == 1 && currentIndex < 0))
}