package com.yourapp.iptv.player.state

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentUrl: String? = null,
    val currentChannelName: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)