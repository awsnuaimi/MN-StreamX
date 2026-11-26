package mnstudio.apps.mnstreamx.ui.screen.player

import android.os.SystemClock
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import mnstudio.apps.mnstreamx.R
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.ui.component.common.AppButton
import mnstudio.apps.mnstreamx.ui.component.common.FocusInputEpoch
import mnstudio.apps.mnstreamx.ui.component.common.Resume
import mnstudio.apps.mnstreamx.ui.component.common.rememberContextFocus

private val PLAYBACK_ERROR_CODE = Regex("ERROR_CODE_[A-Z0-9_]+")

/** Presentation only: a stable action bookmark survives availability and lifecycle changes. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerErrorOverlay(
    error: String?, canRetry: Boolean, hasChannels: Boolean, focus: FocusRequester,
    onRetry: () -> Unit, onChannels: () -> Unit, onBack: () -> Unit,
    recovery: LiveRecoveryState = LiveRecoveryState(), focusActive: Boolean = true
) {
    val title = stringResource(if (recovery.isRecovering) R.string.live_recovery_title else R.string.player_error)
    val errorCode = remember(error) { error?.let { PLAYBACK_ERROR_CODE.find(it)?.value } }
    val actions = remember(canRetry, hasChannels) {
        buildList { if (canRetry) add("retry"); if (hasChannels) add("channels"); add("back") }
    }
    val memory = rememberContextFocus("live-recovery-actions")
    memory.Maintain("actions", actions, ready = focusActive)
    memory.Resume(setOf("actions"), "actions", initial = false)
    memory.Restore("actions", actions, ready = focusActive)
    val entryInputEpoch = remember(focusActive) { FocusInputEpoch.value }
    LaunchedEffect(focusActive) {
        if (focusActive && entryInputEpoch == FocusInputEpoch.value)
            memory.restore(setOf("actions"), "actions")
    }
    @Composable
    fun actionTarget(id: String): Modifier = memory.target("actions", id, actions.indexOf(id))
        .then(if (actions.first() == id) Modifier.focusRequester(focus) else Modifier)
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth().heightIn(max = maxHeight)
            .semantics { paneTitle = title }.testTag("player-recovery-panel"),
            color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                when (recovery.phase) {
                    LiveRecoveryPhase.WAITING_NETWORK -> Text(stringResource(R.string.live_recovery_waiting_network))
                    LiveRecoveryPhase.SCHEDULED -> ScheduledRetryText(recovery)
                    LiveRecoveryPhase.CONNECTING -> Text(stringResource(R.string.live_recovery_connecting,
                        recovery.attempt))
                    else -> Unit
                }
                if (recovery.isRecovering) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (errorCode != null) Text(errorCode, style = MaterialTheme.typography.bodySmall)
                // Actual measured text widths decide wrapping, including German and large fonts.
                FlowRow(Modifier.fillMaxWidth().then(memory.inputModifier())
                    .focusProperties { exit = { direction ->
                        when (direction) {
                            FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down ->
                                FocusRequester.Cancel
                            else -> FocusRequester.Default
                        }
                    } }.focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canRetry) AppButton(stringResource(R.string.player_retry), onRetry,
                        actionTarget("retry"))
                    if (hasChannels) AppButton(stringResource(R.string.player_pick_channel), onChannels,
                        actionTarget("channels"))
                    AppButton(stringResource(R.string.player_back_channels), onBack,
                        actionTarget("back"))
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
    } else stringResource(R.string.live_recovery_scheduled, recovery.attempt)
    Text(label)
}

private fun Long.roundedSeconds(): Long = if (this <= 0) 0 else (this - 1) / 1_000L + 1