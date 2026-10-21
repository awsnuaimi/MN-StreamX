package com.yourapp.iptv.ui.screen.player

internal enum class PlayerPanel { WATCHING, CARDS, MORE, CHANNELS, DETAILS, CONTROLS, HISTORY, CAPTIONS }

internal data class PlayerPanelState(
    val panel: PlayerPanel = PlayerPanel.WATCHING,
    val channelsReturn: PlayerPanel = PlayerPanel.WATCHING,
    val focusMore: Boolean = false
) {
    val isModal: Boolean get() = panel == PlayerPanel.MORE || panel == PlayerPanel.CHANNELS ||
        panel == PlayerPanel.DETAILS || panel == PlayerPanel.HISTORY || panel == PlayerPanel.CAPTIONS
    fun cards() = copy(panel = PlayerPanel.CARDS, focusMore = false)
    fun more() = copy(panel = PlayerPanel.MORE)
    fun channels() = copy(panel = PlayerPanel.CHANNELS,
        channelsReturn = panel.takeIf { it == PlayerPanel.CARDS || it == PlayerPanel.MORE } ?: PlayerPanel.WATCHING)
    fun details() = copy(panel = PlayerPanel.DETAILS)
    fun captions() = copy(panel = PlayerPanel.CAPTIONS)
    fun history() = copy(panel = PlayerPanel.HISTORY)
    fun controls() = copy(panel = PlayerPanel.CONTROLS)
    fun watching() = copy(panel = PlayerPanel.WATCHING, focusMore = false)
    // CH keys preserve an explicitly opened rail; they never open it themselves.
    fun afterZapping() = if (panel == PlayerPanel.CARDS) cards() else watching()
    fun afterSelection() = watching()
    fun closeTop() = when (panel) {
        PlayerPanel.MORE -> copy(panel = PlayerPanel.CARDS, focusMore = true)
        PlayerPanel.DETAILS, PlayerPanel.HISTORY, PlayerPanel.CAPTIONS -> more()
        PlayerPanel.CHANNELS -> copy(panel = channelsReturn, focusMore = channelsReturn == PlayerPanel.CARDS)
        else -> watching()
    }
}