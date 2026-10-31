package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

@Composable
internal fun VodPosterCard(item: VodItem, modifier: Modifier = Modifier,
    onFocused: (VodItem) -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val image = remember(context, item.poster) {
        item.poster?.let { url ->
            ImageRequest.Builder(context).data(url).size(256, 350).crossfade(false).build()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth().tvFocusIndicator()
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused(item)
            },
            color = colors.surfaceContainerLow,
            border = if (focused) BorderStroke(3.dp, colors.primary) else null,
            shape = RoundedCornerShape(12.dp)) {
            Box(Modifier.fillMaxWidth().height(181.dp)) {
                if (item.poster?.startsWith("https://") == true || item.poster?.startsWith("http://") == true) {
                    AsyncImage(model = image, contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(colors.surfaceContainerHigh),
                        contentAlignment = Alignment.Center) {
                        Text("▶", color = colors.primary, style = MaterialTheme.typography.displaySmall)
                    }
                }
                item.year?.takeIf { it.isNotBlank() }?.let { year ->
                    Surface(Modifier.align(Alignment.BottomStart).padding(5.dp),
                        color = colors.surfaceContainerLowest.copy(alpha = .93f),
                        shape = RoundedCornerShape(5.dp)) {
                        Text(year, Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            color = colors.onSurface, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(item.name, Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.labelLarge, color = colors.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.year?.takeIf { it.isNotBlank() }?.let {
            Text(it, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant)
        }
    }
}