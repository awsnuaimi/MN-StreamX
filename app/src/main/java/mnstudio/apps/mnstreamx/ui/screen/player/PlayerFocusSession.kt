package mnstudio.apps.mnstreamx.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import mnstudio.apps.mnstreamx.ui.component.common.ContextFocus
import mnstudio.apps.mnstreamx.ui.component.common.rememberContextFocus

/** Owned by the player destination, so closing a panel does not erase its bookmarks. */
internal val LocalPlayerFocus = staticCompositionLocalOf<ContextFocus?> { null }

@Composable
internal fun rememberPlayerFocus(): ContextFocus =
    LocalPlayerFocus.current ?: rememberContextFocus("standalone-player-panel")

internal val PlayerPanelSaver = listSaver<PlayerPanelState, String>(
    save = {
        val panel = if (it.panel == PlayerPanel.CONTROLS) PlayerPanel.WATCHING else it.panel
        listOf(panel.name, it.channelsReturn.name, it.focusMore.toString())
    },
    restore = { values ->
        PlayerPanelState(
            panel = PlayerPanel.entries.firstOrNull { it.name == values.getOrNull(0) } ?: PlayerPanel.WATCHING,
            channelsReturn = PlayerPanel.entries.firstOrNull { it.name == values.getOrNull(1) } ?: PlayerPanel.WATCHING,
            focusMore = values.getOrNull(2) == "true"
        )
    }
)