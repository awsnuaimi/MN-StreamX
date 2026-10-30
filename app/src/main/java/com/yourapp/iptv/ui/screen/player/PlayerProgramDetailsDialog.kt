@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.yourapp.iptv.ui.screen.player

import android.view.KeyEvent
import com.yourapp.iptv.ui.theme.AppThemeColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.Resume
import com.yourapp.iptv.ui.component.common.rememberContextFocus
import com.yourapp.iptv.ui.presentation.ProgramPresentation
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import kotlinx.coroutines.launch

@Composable
internal fun PlayerProgramDetailsDialog(channelName: String, state: PlayerEpgUiState,
    appearance: PlayerAppearance, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val focus = rememberContextFocus("player-details")
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        focus.Resume(setOf("details"), "details", "content")
        focus.Restore("details", listOf("content", "close"), ready = windowFocused)
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textSize.scale)) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(0.86f).then(focus.inputModifier()), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary,
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
                        }.focusable(), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                        Column(Modifier.verticalScroll(scroll).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val current = state.schedule.current(state.nowMillis)
                            val next = state.schedule.next(state.nowMillis)
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