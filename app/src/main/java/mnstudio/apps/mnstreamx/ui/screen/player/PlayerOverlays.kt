package mnstudio.apps.mnstreamx.ui.screen.player

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.PlaybackTechnicalInfo
import mnstudio.apps.mnstreamx.domain.model.PlayerAppearance
import mnstudio.apps.mnstreamx.ui.screen.player.deck.PlayerDeckOverlay
import mnstudio.apps.mnstreamx.ui.state.PlayerEpgUiState

private const val LIVE_BUFFERING_INDICATOR_DELAY_MS = 750L

@Composable
fun PlayerInfoOverlay(
    channel: Channel?, position: Int, channels: List<Channel>, appearance: PlayerAppearance,
    epg: PlayerEpgUiState, technical: PlaybackTechnicalInfo, cardsOpen: Boolean,
    focused: Channel?, preview: PlayerEpgUiState, focusEpoch: Int, focusMore: Boolean,
    onMore: () -> Unit, onSelect: (String) -> Unit, onFocusChannel: (String?) -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textSize.scale)) {
        if (channel != null) PlayerDeckOverlay(channel, channels, position - 1, appearance, epg, technical,
            cardsOpen, focused, preview, focusEpoch, focusMore, onMore, onSelect, onFocusChannel)
    }
}

@Composable
internal fun LiveBufferingIndicator(showImmediately: Boolean, modifier: Modifier = Modifier) {
    if (!showImmediately) {
        var delayElapsed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(LIVE_BUFFERING_INDICATOR_DELAY_MS)
            delayElapsed = true
        }
        if (!delayElapsed) return
    }
    CircularProgressIndicator(modifier)
}