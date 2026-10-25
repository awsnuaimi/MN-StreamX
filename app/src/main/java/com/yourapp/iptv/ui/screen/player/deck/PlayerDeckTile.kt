package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.presentation.DeckChannelItem
import com.yourapp.iptv.ui.presentation.PlayerTileLayout

@Composable
internal fun DeckShortcutTile(icon: DeckIcon, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    DeckTileSurface(enabled, false, onClick, modifier) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            PlayerDeckIcon(icon, MaterialTheme.colorScheme.onSurface, 30.dp)
            Text(label, Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
internal fun DeckChannelTile(item: DeckChannelItem, playing: Boolean,
    showLogo: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scale = PlayerTileLayout.fontScale(LocalDensity.current.fontScale)
    val playingLabel = stringResource(R.string.player_playing)
    DeckTileSurface(true, playing, onClick, modifier.semantics {
        contentDescription = item.channel.name
        if (playing) stateDescription = playingLabel
    }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height((18f * scale).dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.number.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                if (playing) Text(playingLabel, Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall)
            }
            if (showLogo) ChannelLogo(item.channel.name, item.channel.logoUrl, Modifier.width(88.dp).height(32.dp))
            Text(item.channel.name, Modifier.fillMaxWidth().height((22f * scale).dp), color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content))
        }
    }
}

@Composable
private fun DeckTileSurface(enabled: Boolean, playing: Boolean, onClick: () -> Unit, modifier: Modifier,
    content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.small, contentColor = MaterialTheme.colorScheme.onSurface,
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = when { focused -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary); playing -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline); else -> null },
        content = content)
}