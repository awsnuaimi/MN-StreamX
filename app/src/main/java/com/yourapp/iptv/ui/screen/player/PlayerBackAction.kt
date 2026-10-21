package com.yourapp.iptv.ui.screen.player

internal enum class PlayerBackAction { CLOSE_DETAILS, CLOSE_CHANNELS, CLOSE_MORE, HIDE_CONTROLS, HIDE_CARDS, EXIT, CLOSE_HISTORY, CLOSE_CAPTIONS }

internal fun playerBackAction(panel: PlayerPanel): PlayerBackAction = when (panel) {
    PlayerPanel.DETAILS -> PlayerBackAction.CLOSE_DETAILS
    PlayerPanel.CAPTIONS -> PlayerBackAction.CLOSE_CAPTIONS
    PlayerPanel.HISTORY -> PlayerBackAction.CLOSE_HISTORY
    PlayerPanel.CHANNELS -> PlayerBackAction.CLOSE_CHANNELS
    PlayerPanel.MORE -> PlayerBackAction.CLOSE_MORE
    PlayerPanel.CONTROLS -> PlayerBackAction.HIDE_CONTROLS
    PlayerPanel.CARDS -> PlayerBackAction.HIDE_CARDS
    PlayerPanel.WATCHING -> PlayerBackAction.EXIT
}