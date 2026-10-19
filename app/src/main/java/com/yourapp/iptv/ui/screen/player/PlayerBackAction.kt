package com.yourapp.iptv.ui.screen.player

enum class PlayerBackAction { CLOSE_DETAILS, CLOSE_CHANNELS, HIDE_CONTROLS, HIDE_INFO, EXIT }

fun playerBackAction(detailsOpen: Boolean, channelsOpen: Boolean, controlsVisible: Boolean,
    infoFocused: Boolean): PlayerBackAction = when {
    detailsOpen -> PlayerBackAction.CLOSE_DETAILS
    channelsOpen -> PlayerBackAction.CLOSE_CHANNELS
    controlsVisible -> PlayerBackAction.HIDE_CONTROLS
    infoFocused -> PlayerBackAction.HIDE_INFO
    else -> PlayerBackAction.EXIT
}