package mnstudio.apps.mnstreamx.ui.screen.player

internal enum class PlayerPanel { WATCHING, CARDS, MORE, CHANNELS, DETAILS, CONTROLS, HISTORY, CAPTIONS, RECENTS }

internal data class PlayerPanelState(
    val panel: PlayerPanel = PlayerPanel.WATCHING,
    val channelsReturn: PlayerPanel = PlayerPanel.WATCHING,
    val focusMore: Boolean = false
) {
    val isModal: Boolean get() = panel == PlayerPanel.MORE || panel == PlayerPanel.CHANNELS ||
        panel == PlayerPanel.DETAILS || panel == PlayerPanel.HISTORY || panel == PlayerPanel.CAPTIONS ||
        panel == PlayerPanel.RECENTS
    fun cards() = copy(panel = PlayerPanel.CARDS, focusMore = false)
    fun more() = copy(panel = PlayerPanel.MORE)
    fun channels() = copy(panel = PlayerPanel.CHANNELS,
        channelsReturn = panel.takeIf { it == PlayerPanel.CARDS || it == PlayerPanel.MORE } ?: PlayerPanel.WATCHING)
    fun details() = copy(panel = PlayerPanel.DETAILS)
    fun captions() = copy(panel = PlayerPanel.CAPTIONS)
    fun history() = copy(panel = PlayerPanel.HISTORY)
    fun recents() = copy(panel = PlayerPanel.RECENTS)
    fun controls() = copy(panel = PlayerPanel.CONTROLS)
    fun watching() = copy(panel = PlayerPanel.WATCHING, focusMore = false)
    fun afterZapping() = if (panel == PlayerPanel.CARDS) cards() else watching()
    fun afterSelection() = watching()
    fun closeTop() = when (panel) {
        PlayerPanel.MORE -> copy(panel = PlayerPanel.CARDS, focusMore = true)
        PlayerPanel.DETAILS, PlayerPanel.HISTORY, PlayerPanel.CAPTIONS -> more()
        PlayerPanel.CHANNELS -> copy(panel = channelsReturn, focusMore = channelsReturn == PlayerPanel.CARDS)
        PlayerPanel.RECENTS -> watching()
        else -> watching()
    }
}