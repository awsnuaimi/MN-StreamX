package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yourapp.iptv.domain.model.VodItem

@Composable
internal fun VodPosterCard(item: VodItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        color = if (focused) Color(0xFF24415A) else Color(0xFF101B30),
        border = if (focused) BorderStroke(2.dp, Color.Cyan) else null,
        shape = RoundedCornerShape(10.dp)) {
        Column {
            if (item.poster != null && (item.poster.startsWith("http://") || item.poster.startsWith("https://"))) {
                AsyncImage(model = item.poster, contentDescription = item.name,
                    modifier = Modifier.fillMaxWidth().height(175.dp), contentScale = ContentScale.Crop)
            } else Box(Modifier.fillMaxWidth().height(175.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("▶", color = Color.White, style = MaterialTheme.typography.displayMedium)
            }
            Text(item.name, Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium,
                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}