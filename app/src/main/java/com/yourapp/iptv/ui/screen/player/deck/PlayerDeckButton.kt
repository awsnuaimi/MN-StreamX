package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
internal fun PlayerDeckButton(icon: DeckIcon, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val color = when { !enabled -> Color(0xFF6C7A88); focused -> Color(0xFF101C26)
        selected -> Color(0xFFFFD54F); else -> Color.White }
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.width(78.dp).heightIn(min = 58.dp)
            .onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
        shape = MaterialTheme.shapes.medium, color = if (focused) Color(0xFFEAF2F7) else Color.Transparent,
        contentColor = color, border = if (focused) BorderStroke(2.dp, NeonBlue) else null) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerDeckIcon(icon, color, filled = selected)
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        }
    }
}