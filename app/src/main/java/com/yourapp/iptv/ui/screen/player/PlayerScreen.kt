package com.yourapp.iptv.ui.screen.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.player.PlayerSurface
import com.yourapp.iptv.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(onBack: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val channel = state.channel
    var playbackError by remember(channel?.id) { mutableStateOf<String?>(null) }
    var buffering by remember(channel?.id) { mutableStateOf(true) }
    var retry by remember(channel?.id) { mutableIntStateOf(0) }
    val errorFocus = remember { FocusRequester() }
    val error = state.errorMessage ?: playbackError
    LaunchedEffect(error) {
        if (error != null) {
            withFrameNanos { }
            runCatching { errorFocus.requestFocus() }
        }
    }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (channel != null) {
            key(channel.id, retry) {
                PlayerSurface(
                    url = channel.streamUrl,
                    onError = { playbackError = it },
                    onBuffering = { buffering = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppButton("رجوع للقنوات", onBack)
            Text(channel?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        if ((state.isLoading || buffering) && playbackError == null && state.errorMessage == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (error != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                color = Color(0xF0111C36), shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = Color.White)
                    if (channel != null) {
                        AppButton("إعادة التشغيل", {
                            playbackError = null
                            buffering = true
                            retry++
                        }, modifier = Modifier.focusRequester(errorFocus))
                    }
                    AppButton("العودة للقنوات", onBack,
                        modifier = if (channel == null) Modifier.focusRequester(errorFocus) else Modifier)
                }
            }
        }
    }
}