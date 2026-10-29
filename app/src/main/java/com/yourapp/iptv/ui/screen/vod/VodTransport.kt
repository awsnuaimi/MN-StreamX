package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.VodSeek
import com.yourapp.iptv.player.vod.VodPlayerSnapshot

@Composable
internal fun VodTransport(snapshot: VodPlayerSnapshot, labels: VodPlaybackLabels, actions: VodPlayerActions,
    play: FocusRequester, up: FocusRequester, down: FocusRequester, modifier: Modifier = Modifier, feedback: Pair<Long,Long>? = null) {
    val pause = snapshot.playWhenReady && !snapshot.ended
    Column(modifier.fillMaxWidth(),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(9.dp)) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(24.dp,Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            VodTransportButton(labels.rewind(),VodGlyph.REWIND,{ actions.step(-VodSeek.STEP_MS) },
                Modifier.focusProperties { this.up = up;this.down = down }.testTag("vod-rewind"),snapshot.seekable)
            VodTransportButton(if (pause) labels.pause() else labels.play(),if (pause) VodGlyph.PAUSE else VodGlyph.PLAY,actions.toggle,
                Modifier.focusRequester(play).focusProperties { this.up = up;this.down = down }.testTag("vod-toggle"),primary = true,buffering = snapshot.buffering)
            VodTransportButton(labels.forward(),VodGlyph.FORWARD,{ actions.step(VodSeek.STEP_MS) },
                Modifier.focusProperties { this.up = up;this.down = down }.testTag("vod-forward"),snapshot.seekable)
        }
    }
    Surface(color = Color(0xDD0C121B),contentColor = Color.White,shape = CircleShape,
        border = BorderStroke(1.dp,Color.White.copy(alpha = .12f))) {
    Text(if (feedback != null) "${if (feedback.first < 0) labels.rewind() else labels.forward()} · ${VodSeek.time(feedback.second)}"
        else if (snapshot.buffering) labels.buffering() else if (pause) labels.pause() else labels.play(),
        Modifier.padding(horizontal = 14.dp,vertical = 5.dp).heightIn(min = 18.dp).then(if (feedback != null) Modifier.testTag("vod-seek-feedback") else Modifier),
        color = Color.White.copy(alpha = .92f),fontSize = 12.sp,maxLines = 1)
    }
    }
}

@Composable
private fun VodTransportButton(label: String, glyph: VodGlyph, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, primary: Boolean = false, buffering: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Button(onClick,modifier.size(if (primary) 86.dp else 62.dp)
            .focusProperties { canFocus = enabled }.onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .border(if (focused) 2.dp else 0.dp,if (focused) accent else Color.Transparent,CircleShape).padding(5.dp)
            .shadow(if (primary) 12.dp else 4.dp,CircleShape),enabled = enabled,shape = CircleShape,
            contentPadding = PaddingValues(0.dp),border = BorderStroke(1.dp,Color.White.copy(alpha = if (primary) .95f else .22f)),
            colors = ButtonDefaults.buttonColors(containerColor = if (primary || focused) Color.White else Color(0xCB141B25),
                contentColor = if (primary || focused) Color(0xFF111820) else Color.White,
                disabledContainerColor = Color(0x9A141B25),disabledContentColor = Color.White.copy(alpha = .35f))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (primary && buffering) CircularProgressIndicator(Modifier.size(28.dp),color = Color(0xFF111820),strokeWidth = 2.dp)
                else VodControlIcon(glyph,Modifier.size(if (primary) 30.dp else 19.dp))
                if (!primary) Text("10",fontSize = 9.sp,lineHeight = 11.sp)
            }
        }

    }
}