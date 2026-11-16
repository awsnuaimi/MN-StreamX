package mnstudio.apps.mnstreamx.ui.screen.player

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import mnstudio.apps.mnstreamx.R
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.domain.model.PlaybackTechnicalInfo
import mnstudio.apps.mnstreamx.domain.model.PlayerAppearance
import mnstudio.apps.mnstreamx.ui.component.common.AppButton
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

@Composable
fun PlayerErrorOverlay(
    error: String?, canRetry: Boolean, hasChannels: Boolean, focus: FocusRequester,
    onRetry: () -> Unit, onChannels: () -> Unit, onBack: () -> Unit,
    recovery: LiveRecoveryState = LiveRecoveryState()
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(32.dp), color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(if (recovery.isRecovering) R.string.live_recovery_title else R.string.player_error),
                    style = MaterialTheme.typography.titleMedium)
                when (recovery.phase) {
                    LiveRecoveryPhase.WAITING_NETWORK -> Text(stringResource(R.string.live_recovery_waiting_network))
                    LiveRecoveryPhase.SCHEDULED -> ScheduledRetryText(recovery)
                    LiveRecoveryPhase.CONNECTING -> Text(stringResource(R.string.live_recovery_connecting,
                        recovery.attempt))
                    else -> Unit
                }
                if (recovery.isRecovering) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!recovery.isRecovering && error != null) {
                    Regex("ERROR_CODE_[A-Z0-9_]+").find(error)?.value?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canRetry) AppButton(stringResource(R.string.player_retry), onRetry, Modifier.focusRequester(focus))
                    if (hasChannels) AppButton(stringResource(R.string.player_pick_channel), onChannels,
                        if (!canRetry) Modifier.focusRequester(focus) else Modifier)
                    AppButton(stringResource(R.string.player_back_channels), onBack,
                        if (!canRetry && !hasChannels) Modifier.focusRequester(focus) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun ScheduledRetryText(recovery: LiveRecoveryState) {
    val deadline = recovery.retryAtElapsedRealtimeMs
    if (deadline == null) {
        Text(stringResource(R.string.live_recovery_scheduled, recovery.attempt))
        return
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var seconds by remember(deadline) {
        mutableLongStateOf((deadline - SystemClock.elapsedRealtime()).roundedSeconds())
    }
    LaunchedEffect(deadline, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val remainingMs = deadline - SystemClock.elapsedRealtime()
                seconds = remainingMs.roundedSeconds()
                if (remainingMs <= 0) break
                delay((remainingMs - 1) % 1_000L + 1)
            }
        }
    }
    val label = if (seconds > 0L) {
        val minutesText = (seconds / 60).toString().padStart(2, '0')
        val secondsText = (seconds % 60).toString().padStart(2, '0')
        stringResource(R.string.live_recovery_countdown, recovery.attempt, "$minutesText:$secondsText")
    } else {
        stringResource(R.string.live_recovery_scheduled, recovery.attempt)
    }
    Text(label)
}

private fun Long.roundedSeconds(): Long = if (this <= 0) 0 else (this - 1) / 1_000L + 1