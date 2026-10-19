package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.presentation.DeckChannelItem
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
internal fun DeckShortcutTile(icon: DeckIcon, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    DeckTileSurface(enabled, false, onClick, modifier) { color ->
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            PlayerDeckIcon(icon, color, 30.dp)
            Text(label, Modifier.padding(top = 8.dp), maxLines = 2, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun DeckChannelTile(item: DeckChannelItem, playing: Boolean, subtitle: String,
    showLogo: Boolean, onClick: () -> Unit, modifier: Modifier) {
    DeckTileSurface(true, playing, onClick, modifier) { color ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (showLogo) ChannelLogo(item.channel.name, item.channel.logoUrl, Modifier.size(32.dp))
                Text("${item.number} · ${item.channel.name}", Modifier.padding(top = 4.dp), color = color,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content))
            }
            Text(subtitle, Modifier.fillMaxWidth().background(if (playing) Color(0xFF12647B) else Color(0xFF233846))
                .padding(horizontal = 8.dp, vertical = 5.dp), color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content))
        }
    }
}

@Composable
private fun DeckTileSurface(enabled: Boolean, playing: Boolean, onClick: () -> Unit, modifier: Modifier,
    content: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val color = when { !enabled -> Color(0xFF748595); focused -> Color(0xFF101C26); else -> Color.White }
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.small, contentColor = color,
        color = if (focused) Color(0xFFE7EFF4) else Color(0xFF1D2C36),
        border = when { focused -> BorderStroke(3.dp, NeonBlue); playing -> BorderStroke(2.dp, NeonBlue); else -> null }) {
        content(color)
    }
}