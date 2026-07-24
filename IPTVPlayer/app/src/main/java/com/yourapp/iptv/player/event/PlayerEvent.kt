package com.yourapp.iptv.player.event

sealed class PlayerEvent {
    data class Play(val url: String, val channelName: String? = null) : PlayerEvent()
    data object Pause : PlayerEvent()
    data object Resume : PlayerEvent()
    data object Stop : PlayerEvent()
    data object Release : PlayerEvent()
}