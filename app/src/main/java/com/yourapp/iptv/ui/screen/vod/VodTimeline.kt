package com.yourapp.iptv.ui.screen.vod

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
    onSeek: (Long) -> Unit, onStep: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val duration = snapshot.duration.coerceAtLeast(1).toFloat()
    val position = drag ?: snapshot.position.toFloat().coerceIn(0f,duration)
    // Physical left/right retain the same seek direction in every app language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column {
            Slider(value = position, onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { onSeek(it.toLong()) }; drag = null },
                valueRange = 0f..duration, enabled = snapshot.seekable,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("vod-timeline")
                    .semantics { contentDescription = labels.timeline() }
                    .onPreviewKeyEvent { event ->
                        val key = event.nativeKeyEvent
                        val delta = when (key.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> -VodSeek.STEP_MS
                            KeyEvent.KEYCODE_DPAD_RIGHT -> VodSeek.STEP_MS
                            else -> null
                        }
                        if (delta != null && snapshot.seekable) {
                            if (key.action == KeyEvent.ACTION_DOWN) { drag = null; onStep(delta) }
                            key.action == KeyEvent.ACTION_DOWN || key.action == KeyEvent.ACTION_UP
                        } else false
                    })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(VodSeek.time(drag?.toLong() ?: snapshot.position),color = Color.White,style = MaterialTheme.typography.bodyMedium)
                Text(VodSeek.time(snapshot.duration),color = Color.White,style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}