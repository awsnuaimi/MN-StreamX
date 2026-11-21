package mnstudio.apps.mnstreamx.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.viewmodel.WatchHistoryViewModel

/** In-player, right-anchored quick recall. No new database or polling while watching. */
@Composable
internal fun PlayerRecentChannelsRail(
    playingId: String?, expectedSource: String,
    onDismiss: () -> Unit, onPlay: (Channel, String) -> Unit,
    history: WatchHistoryViewModel = hiltViewModel(key = "player-recent-channels")
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, history) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) history.resume()
            if (event == Lifecycle.Event.ON_STOP) history.pause()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) history.resume()
        else history.pause()
        onDispose { lifecycle.removeObserver(observer); history.pause() }
    }
    val state by history.uiState.collectAsStateWithLifecycle()
    PlayerRecentChannelsRailContent(state, playingId, expectedSource, onDismiss, onPlay, history::reload)
}