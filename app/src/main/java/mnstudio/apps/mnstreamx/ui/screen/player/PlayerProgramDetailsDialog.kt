@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package mnstudio.apps.mnstreamx.ui.screen.player

import android.view.KeyEvent
import mnstudio.apps.mnstreamx.ui.theme.AppThemeColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import mnstudio.apps.mnstreamx.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import mnstudio.apps.mnstreamx.domain.model.PlayerAppearance
import mnstudio.apps.mnstreamx.domain.model.Program
import mnstudio.apps.mnstreamx.ui.component.common.AppButton
import mnstudio.apps.mnstreamx.ui.component.common.Resume
import mnstudio.apps.mnstreamx.ui.component.common.rememberContextFocus
import mnstudio.apps.mnstreamx.ui.presentation.ProgramPresentation
import mnstudio.apps.mnstreamx.ui.state.PlayerEpgUiState
import kotlinx.coroutines.launch

@Composable
internal fun PlayerProgramDetailsDialog(channelName: String, state: PlayerEpgUiState,
    appearance: PlayerAppearance, onDismiss: () -> Unit, fromInfoKey: Boolean = false) {
    val density = LocalDensity.current
    val focus = rememberContextFocus("player-details")
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !fromInfoKey)
    ) {
        if (fromInfoKey) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setDimAmount(0.10f) }
        }
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        focus.Resume(setOf("details"), "details", "content")
        focus.Restore("details", listOf("content", "close"), ready = windowFocused)
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textSize.scale)) {
            val colors = MaterialTheme.colorScheme
            val bounds = if (fromInfoKey) {
                Modifier.widthIn(max = 800.dp).fillMaxWidth(0.78f).fillMaxHeight(0.66f)
            } else {
                Modifier.fillMaxWidth().fillMaxHeight(0.86f)
            }
            Surface(
                bounds.then(focus.inputModifier()),
                color = if (fromInfoKey) colors.surface.copy(alpha = 0.92f) else colors.surface,
                contentColor = colors.onSurface,
                shape = MaterialTheme.shapes.large,
                border = if (fromInfoKey) BorderStroke(1.dp, colors.outlineVariant) else null
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val heading = if (fromInfoKey) "ⓘ  $channelName" else channelName
                    Text(heading, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.primary,
                        style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content))
                    Surface(Modifier.weight(1f).fillMaxWidth().then(focus.target("details", "content", 0))
                        .onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent {
                            val event = it.nativeKeyEvent
                            val direction = when (event.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> 1
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> -1
                                else -> 0
                            }
                            val canScroll = if (direction > 0) scroll.canScrollForward else scroll.canScrollBackward
                            if (direction == 0 || !canScroll) false
                            else if (event.action == KeyEvent.ACTION_DOWN) {
                                scope.launch { scroll.scrollTo((scroll.value + direction * 140).coerceIn(0, scroll.maxValue)) }
                                true
                            } else event.action == KeyEvent.ACTION_UP
                        }.focusable(), color = if (fromInfoKey) Color.Transparent else colors.surface,
                        contentColor = colors.onSurface,
                        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                        Column(Modifier.verticalScroll(scroll).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val current = state.schedule.current(state.nowMillis)
                            val next = if (fromInfoKey) null else state.schedule.next(state.nowMillis)
                            if (current != null) ProgramDetailsText(stringResource(R.string.player_current_program), current)
                            else Text(playerEpgStatus(state))
                            if (next != null) ProgramDetailsText(stringResource(R.string.player_next_program), next)
                            if (state.schedule.isStale) Text(stringResource(R.string.player_cached), color = AppThemeColors.warning(MaterialTheme.colorScheme))
                        }
                    }
                    AppButton(stringResource(R.string.player_close_details), onDismiss,
                        Modifier.fillMaxWidth().then(focus.target("details", "close", 1)))
                }
            }
        }
    }
}

@Composable
private fun ProgramDetailsText(label: String, program: Program) {
    Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    Text(program.title, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content))
    Text(ProgramPresentation.timeRange(program.startTime, program.endTime), color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
    Text(program.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_no_description),
        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
}