package com.yourapp.iptv.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.caption.CaptionPresentation
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.player.caption.CaptionAudioTap
import com.yourapp.iptv.viewmodel.CaptionViewModel
import kotlinx.coroutines.delay

@Composable
internal fun GeneratedCaptionOverlay(model: CaptionViewModel, input: CaptionAudioTap?, settings: CaptionSettings,
    raised: Boolean) {
    if (settings.mode != CaptionMode.GENERATED) return
    val state by model.generated.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf("") }
    LaunchedEffect(state.cue, input) {
        while (true) {
            visible = CaptionPresentation.visible(state.cue, input?.positionUs ?: Long.MIN_VALUE, System.nanoTime() / 1000000)
            delay(150)
        }
    }
    val message = if (visible.isNotBlank()) visible else when (state.progress.status) {
        CaptionStatus.OFF, CaptionStatus.LISTENING -> ""
        else -> captionStatusText(state.progress)
    }
    if (message.isBlank()) return
    Box(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = if (raised) 148.dp else 30.dp)) {
        Text(message, color = Color.White, textAlign = TextAlign.Center,
            fontSize = if (visible.isNotBlank()) settings.textSize.sp.sp else 16.sp,
            maxLines = 3, modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 950.dp)
                .background(if (settings.shaded) Color.Black.copy(alpha = 0.78f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 6.dp))
    }
}