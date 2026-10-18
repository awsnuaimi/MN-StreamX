package com.yourapp.iptv.ui.state

import com.yourapp.iptv.domain.model.Channel

data class HomeUiState(
    val channels: List<Channel> = emptyList(),
    val visibleChannels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val query: String = "",
    val selectedGroup: String? = null,
    val selectedChannelId: String? = null,
    val isLoading: Boolean = true,
    val needsSource: Boolean = false,
    val errorMessage: String? = null
)