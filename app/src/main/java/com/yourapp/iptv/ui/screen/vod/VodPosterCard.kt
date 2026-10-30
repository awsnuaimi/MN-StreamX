package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

@Composable
internal fun VodPosterCard(item: VodItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, modifier = modifier.tvFocusIndicator().fillMaxWidth().onFocusChanged { focused = it.isFocused },
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(10.dp)) {
        Column {
            if (item.poster != null && (item.poster.startsWith("http://") || item.poster.startsWith("https://"))) {
                AsyncImage(model = item.poster, contentDescription = item.name,
                    modifier = Modifier.fillMaxWidth().height(175.dp), contentScale = ContentScale.Crop)
            } else Box(Modifier.fillMaxWidth().height(175.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("▶", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.displayMedium)
            }
            Text(item.name, Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}