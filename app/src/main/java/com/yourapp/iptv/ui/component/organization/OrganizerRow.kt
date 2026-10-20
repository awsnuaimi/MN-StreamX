package com.yourapp.iptv.ui.component.organization

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
fun OrganizerRow(title: String, modifier: Modifier = Modifier, subtitle: String = "",
    selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.semantics { this.selected = selected },
        shape = MaterialTheme.shapes.small, color = if (focused || selected) Color(0xFF164362) else Color(0xFF101C32),
        contentColor = Color.White, border = if (focused) BorderStroke(2.dp, NeonBlue) else null) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
            if (subtitle.isNotBlank()) Text(subtitle, color = Color(0xFFB3C6D9), maxLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
        }
    }
}