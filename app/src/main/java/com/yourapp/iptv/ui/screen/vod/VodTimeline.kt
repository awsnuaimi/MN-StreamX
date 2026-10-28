package com.yourapp.iptv.ui.screen.vod

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.yourapp.iptv.domain.model.VodSeek
import com.yourapp.iptv.player.vod.VodPlayerSnapshot

@Composable
internal fun VodTimeline(snapshot: VodPlayerSnapshot, labels: VodPlaybackLabels, focus: FocusRequester,
    onSeek: (Long) -> Unit, onStep: (Long) -> Unit, onScrubbing: (Boolean) -> Unit = {}, down: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val release by rememberUpdatedState(onScrubbing)
    DisposableEffect(Unit) { onDispose { release(false) } }
    LaunchedEffect(snapshot.seekable) { if (!snapshot.seekable) { drag = null;release(false) } }
    val duration = snapshot.duration.coerceAtLeast(1).toFloat()
    val position = drag ?: snapshot.position.toFloat().coerceIn(0f,duration)
    val loaded = if (snapshot.duration > 0) (snapshot.buffered.toFloat()/duration).coerceIn(0f,1f) else 0f
    val elapsed = drag?.toLong() ?: snapshot.position.coerceAtLeast(0)
    val remaining = if (snapshot.duration > 0) snapshot.duration - elapsed.coerceIn(0,snapshot.duration) else -1
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column {
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                Text(VodSeek.time(elapsed),Modifier.testTag("vod-elapsed"),color = Color.White,style = MaterialTheme.typography.bodyMedium)
                Text(" / ${VodSeek.time(snapshot.duration)}",color = Color.White.copy(alpha = .65f),style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(labels.remaining(VodSeek.time(remaining)),Modifier.testTag("vod-remaining"),color = Color.White.copy(alpha = .8f),
                    style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.fillMaxWidth(),contentAlignment = Alignment.Center) {
                LinearProgressIndicator(progress = { loaded },
                    modifier = Modifier.fillMaxWidth().testTag("vod-buffered"),color = Color.White.copy(alpha = .4f),trackColor = Color.White.copy(alpha = .15f))
                Slider(value = position,onValueChange = { drag = it;release(true) },
                    onValueChangeFinished = { drag?.let { onSeek(it.toLong()) };drag = null;release(false) },
                    valueRange = 0f..duration,enabled = snapshot.seekable,
                    colors = SliderDefaults.colors(inactiveTrackColor = Color.Transparent,disabledInactiveTrackColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().border(if (focused) 2.dp else 0.dp,
                        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,RoundedCornerShape(10.dp))
                        .onFocusChanged { focused = it.isFocused }.focusRequester(focus).focusProperties { down?.let { this.down = it } }
                        .testTag("vod-timeline").semantics { contentDescription = labels.timeline() }
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            val delta = when (key.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> -VodSeek.STEP_MS
                                KeyEvent.KEYCODE_DPAD_RIGHT -> VodSeek.STEP_MS
                                else -> null
                            }
                            if (delta != null && snapshot.seekable) {
                                if (key.action == KeyEvent.ACTION_DOWN) { drag = null;release(false);onStep(delta) }
                                key.action == KeyEvent.ACTION_DOWN || key.action == KeyEvent.ACTION_UP
                            } else false
                        })
            }
            if (!snapshot.seekable) Text(labels.seekUnavailable(),color = Color.White.copy(alpha = .65f),style = MaterialTheme.typography.labelSmall)
        }
    }
}