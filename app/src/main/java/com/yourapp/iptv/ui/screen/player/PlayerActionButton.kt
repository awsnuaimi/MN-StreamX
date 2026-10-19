package com.yourapp.iptv.ui.screen.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
internal fun PlayerActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp).onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.medium,
        color = if (focused) Color(0xFF164F6D) else Color(0xFF17243D),
        contentColor = if (enabled) Color.White else Color(0xFF8894A6),
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) NeonBlue else Color(0xFF3C536F))) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
    }
}