package com.yourapp.iptv.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
fun PlayerInfoOverlay(
    channel: Channel?, position: Int, total: Int, canChange: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onChannels: () -> Unit,
    onControls: () -> Unit, onBack: () -> Unit, onFocusChanged: (Boolean) -> Unit,
    epg: PlayerEpgUiState
) {
    Box(Modifier.fillMaxSize().onFocusChanged { onFocusChanged(it.hasFocus) }.focusGroup()) {
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.76f)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppButton("رجوع للقنوات", onBack)
            if (channel != null) ChannelLogo(channel.name, channel.logoUrl, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text(channel?.name ?: "MN StreamX", color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                channel?.group?.let {
                    Text(it, color = NeonBlue, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("$position / $total", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.76f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (channel != null) PlayerEpgInfo(epg)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton("السابق", onPrevious, enabled = canChange)
                AppButton("قائمة القنوات", onChannels, enabled = total > 0)
                AppButton("التالي", onNext, enabled = canChange)
                AppButton("تحكم بالتشغيل", onControls, enabled = channel != null)
            }
            Text("↑ / CH+ التالي · ↓ / CH− السابق · ← / MENU القائمة · OK تحكم",
                color = Color(0xFFCCD9E8), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PlayerErrorOverlay(
    error: String, canRetry: Boolean, hasChannels: Boolean, focus: FocusRequester,
    onRetry: () -> Unit, onChannels: () -> Unit, onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(32.dp), color = Color(0xF0111C36),
            shape = MaterialTheme.shapes.large, contentColor = Color.White) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(error, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canRetry) AppButton("إعادة التشغيل", onRetry, Modifier.focusRequester(focus))
                    if (hasChannels) AppButton("اختيار قناة", onChannels,
                        if (!canRetry) Modifier.focusRequester(focus) else Modifier)
                    AppButton("العودة للقنوات", onBack,
                        if (!canRetry && !hasChannels) Modifier.focusRequester(focus) else Modifier)
                }
            }
        }
    }
}