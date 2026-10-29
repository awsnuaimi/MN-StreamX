package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun VodCinemaPanel(modifier: Modifier = Modifier, scroll: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier,color = Color(0xFA0D131C),contentColor = Color.White,shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp,Color.White.copy(alpha = .15f)),tonalElevation = 0.dp,shadowElevation = 18.dp) {
        Column(Modifier.padding(22.dp).then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),verticalArrangement = Arrangement.spacedBy(12.dp),content = content)
    }
}

@Composable
internal fun VodPanelTitle(title: String, glyph: VodGlyph) {
    Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(14.dp),color = Color.White.copy(alpha = .08f),contentColor = Color.White) {
            Box(Modifier.size(44.dp),contentAlignment = Alignment.Center) { VodControlIcon(glyph,Modifier.size(23.dp)) }
        }
        Text(title,style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun VodChoiceRow(label: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: VodGlyph? = null) {
    var focused by remember { mutableStateOf(false) }
    Button(onClick,modifier.fillMaxWidth().heightIn(min = 52.dp).focusProperties { canFocus = true }
        .onFocusChanged { focused = it.isFocused }.semantics { role = if (glyph == null) Role.RadioButton else Role.Button;if (glyph == null) selected = chosen },
        shape = RoundedCornerShape(14.dp),contentPadding = PaddingValues(horizontal = 16.dp,vertical = 12.dp),
        border = BorderStroke(if (focused) 2.dp else 1.dp,if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .07f)),
        colors = ButtonDefaults.buttonColors(containerColor = if (focused) Color.White else Color.White.copy(alpha = if (chosen) .09f else .025f),
            contentColor = if (focused) Color(0xFF111820) else Color.White)) {
        Text(label,Modifier.weight(1f),maxLines = 2,overflow = TextOverflow.Ellipsis)
        val ink = LocalContentColor.current
        if (glyph != null) VodControlIcon(glyph,Modifier.padding(start = 14.dp).size(18.dp))
        else Canvas(Modifier.padding(start = 14.dp).size(18.dp)) {
            drawCircle(ink.copy(alpha = if (chosen) 1f else .4f),radius = size.minDimension/2-1.dp.toPx(),style = Stroke(1.5.dp.toPx()))
            if (chosen) drawCircle(ink,radius = size.minDimension*.22f,center = Offset(size.width/2,size.height/2))
        }
    }
}