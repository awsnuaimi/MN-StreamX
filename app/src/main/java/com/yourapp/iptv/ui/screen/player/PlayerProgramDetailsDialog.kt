package com.yourapp.iptv.ui.screen.player

import android.view.KeyEvent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.model.Program
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.presentation.ProgramPresentation
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.ui.theme.NeonBlue
import kotlinx.coroutines.launch

@Composable
internal fun PlayerProgramDetailsDialog(channelName: String, state: PlayerEpgUiState,
    appearance: PlayerAppearance, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textSize.scale)) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(0.86f), color = Color(0xFF101C32), contentColor = Color.White,
                shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = NeonBlue,
                        style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content))
                    Surface(Modifier.weight(1f).fillMaxWidth().focusRequester(focus)
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
                        }.focusable(), color = Color(0xFF101C32), contentColor = Color.White,
                        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) NeonBlue else Color(0xFF263658))) {
                        Column(Modifier.verticalScroll(scroll).padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val current = state.schedule.current(state.nowMillis)
                            val next = state.schedule.next(state.nowMillis)
                            if (current != null) ProgramDetailsText("البرنامج الحالي", current)
                            else Text(playerEpgStatus(state))
                            if (next != null) ProgramDetailsText("البرنامج التالي", next)
                            if (state.schedule.isStale) Text("هذه معلومات محفوظة مؤقتًا.", color = Color(0xFFFFD180))
                        }
                    }
                    AppButton("إغلاق التفاصيل", onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ProgramDetailsText(label: String, program: Program) {
    Text(label, color = NeonBlue, style = MaterialTheme.typography.labelLarge)
    Text(program.title, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content))
    Text(ProgramPresentation.timeRange(program.startTime, program.endTime), color = Color(0xFFBCCDE1),
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
    Text(program.description?.takeIf { it.isNotBlank() } ?: "لا يتوفر وصف لهذا البرنامج.",
        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
}