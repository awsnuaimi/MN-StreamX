package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.domain.model.HomeAppearance
import com.yourapp.iptv.domain.model.HomeTheme

/** Reference colours are scoped to the live browser; the existing red/light/night modes remain usable. */
internal fun liveTvColors(appearance: HomeAppearance): HomeTvPalette {
    val base = HomeTvPalettes.forTheme(appearance.theme, appearance.isNightMode)
    return if (appearance.theme != HomeTheme.DARK_BLUE || appearance.isNightMode) base else base.copy(
        background = Color(0xFF071127), sidebar = Color(0xFF061024), surface = Color(0xFF122045),
        focused = Color(0xFF1A346C), selected = Color(0xFF0F1C3D), outline = Color(0xFF263655),
        text = Color.White, muted = Color(0xFF92A2BF), accent = Color(0xFF00B5EC))
}

internal fun liveTvOnAccent(colors: HomeTvPalette) = if (colors.text.luminance() < .5f) Color.White else Color(0xFF031421)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveTvPill(text: String, colors: HomeTvPalette, onClick: () -> Unit,
    modifier: Modifier = Modifier, selected: Boolean = false, compact: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 32.dp)
        .focusProperties { canFocus = true }.onFocusChanged { focused = it.isFocused },
        shape = if (selected) HomeHeroShape else RoundedCornerShape(50),
        color = if (selected) colors.accent else Color.Transparent,
        border = if (selected) null else BorderStroke(if (focused) 2.dp else 1.dp,
            if (focused) colors.accent else colors.outline)) {
        Box(Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) liveTvOnAccent(colors) else colors.text, fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    }
}