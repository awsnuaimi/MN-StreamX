package com.yourapp.iptv.ui.component.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/** Two contrasting inset strokes remain visible on selected buttons and inside clipped lists. */
internal fun Modifier.tvFocusIndicator(shape: Shape = RoundedCornerShape(10.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    onFocusChanged { focused = it.isFocused }.drawWithContent {
        drawContent()
        if (focused) {
            val inset = 3.dp.toPx()
            val innerSize = Size((size.width - inset * 2).coerceAtLeast(0f),
                (size.height - inset * 2).coerceAtLeast(0f))
            val outline = shape.createOutline(innerSize, layoutDirection, this)
            translate(inset, inset) {
                drawOutline(outline, colors.onSurface, style = Stroke(5.dp.toPx()))
                drawOutline(outline, colors.primary, style = Stroke(2.dp.toPx()))
            }
        }
    }
}