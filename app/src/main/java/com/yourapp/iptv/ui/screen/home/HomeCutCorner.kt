package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

internal val HomeHeroShape = CutCornerShape(topEnd = 10.dp, bottomStart = 10.dp)

@Composable
internal fun HomeWatchButton(text: String, colors: HomeTvPalette, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.tvFocusIndicator(HomeHeroShape).height((35f * scale).dp).onFocusChanged { focused = it.isFocused },
        color = colors.accent, contentColor = if (colors.text == Color.White) Color(0xFF031421) else Color.White,
        shape = HomeHeroShape, border = if (focused) BorderStroke(2.dp, colors.text) else null) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}