package com.yourapp.iptv.ui.component.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel

@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit, modifier: Modifier = Modifier, showGroup: Boolean = true,
    showLogos: Boolean = true, compact: Boolean = false, listMode: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = if (!compact) 92.dp else if (listMode) 64.dp else 80.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.large,
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                ChannelLogo(channel.name, channel.logoUrl, Modifier.size(if (compact) 32.dp else 44.dp),
                    showImage = showLogos)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(channel.name, maxLines = if (listMode) 1 else 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    if (showGroup && (!compact || listMode)) channel.group?.let {
                        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
                FavoriteBadge(channel.isFavorite)
            }
            if (showGroup && compact && !listMode) channel.group?.let {
                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}