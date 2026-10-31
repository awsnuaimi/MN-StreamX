package com.yourapp.iptv.ui.screen.vod

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VodTimeline(snapshot: VodPlayerSnapshot, labels: VodPlaybackLabels, focus: FocusRequester,
    onSeek: (Long) -> Unit, onStep: (Long) -> Unit, onScrubbing: (Boolean) -> Unit = {}, down: FocusRequester? = null, up: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val release by rememberUpdatedState(onScrubbing)
    DisposableEffect(Unit) { onDispose { release(false) } }
    LaunchedEffect(snapshot.seekable) { if (!snapshot.seekable) { drag = null;release(false) } }
    val duration = snapshot.duration.coerceAtLeast(1).toFloat()
    val position = if (snapshot.duration > 0) drag ?: snapshot.position.toFloat().coerceIn(0f,duration) else 0f
    val loaded = if (snapshot.duration > 0) (snapshot.buffered.toFloat()/duration).coerceIn(0f,1f) else 0f
    val accent = MaterialTheme.colorScheme.primary
    val elapsed = drag?.toLong() ?: snapshot.position.coerceAtLeast(0)
    val remaining = if (snapshot.duration > 0) snapshot.duration - elapsed.coerceIn(0,snapshot.duration) else -1
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column {
            if (drag != null) {
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp),contentAlignment = Alignment.Center) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        color = Color(0xF00D1D3E),contentColor = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp,accent)) {
                        Text(VodSeek.time(elapsed),Modifier.padding(horizontal = 16.dp,vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Box(Modifier.fillMaxWidth(),contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(3.dp).testTag("vod-buffered").progressSemantics(loaded)) {
                    val y = size.height/2
                    drawLine(Color.White.copy(alpha = .18f),Offset(0f,y),Offset(size.width,y),size.height,StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = .34f),Offset(0f,y),Offset(size.width*loaded,y),size.height,StrokeCap.Round)
                }
                Slider(value = position,onValueChange = { drag = it;release(true) },
                    onValueChangeFinished = { drag?.let { onSeek(it.toLong()) };drag = null;release(false) },
                    valueRange = 0f..duration,enabled = snapshot.seekable,
                    thumb = {
                        Box(Modifier.size(16.dp).border(if (focused) 2.dp else 0.dp,if (focused) accent else Color.Transparent,CircleShape).padding(if (focused) 0.dp else 2.dp)
                            .background(if (snapshot.seekable) Color.White else Color.Transparent,CircleShape))
                    },
                    track = {
                        Canvas(Modifier.fillMaxWidth().height(if (focused) 6.dp else 5.dp)) {
                            val progress = if (snapshot.duration > 0) (position/duration).coerceIn(0f,1f) else 0f
                            drawLine(accent,Offset(0f,size.height/2),Offset(size.width*progress,size.height/2),size.height,StrokeCap.Round)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusRequester(focus)
                        .focusProperties { down?.let { this.down = it };up?.let { this.up = it } }
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
            Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                Text(VodSeek.time(elapsed),Modifier.testTag("vod-elapsed"),color = Color.White,style = MaterialTheme.typography.bodySmall)
                Text(" / ${VodSeek.time(snapshot.duration)}",color = Color.White.copy(alpha = .55f),style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(labels.remaining(VodSeek.time(remaining)),Modifier.testTag("vod-remaining"),color = Color.White.copy(alpha = .65f),
                    style = MaterialTheme.typography.labelSmall)
            }
            if (!snapshot.seekable) Text(labels.seekUnavailable(),color = Color.White.copy(alpha = .65f),style = MaterialTheme.typography.labelSmall)
        }
    }
}