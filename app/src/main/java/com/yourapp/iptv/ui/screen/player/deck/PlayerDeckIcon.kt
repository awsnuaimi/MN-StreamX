package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class DeckIcon { GUIDE, CHANNELS, FAVORITE, CONTROLS, DETAILS, APPEARANCE, PREVIOUS, NEXT, BACK, MORE }

@Composable
internal fun PlayerDeckIcon(icon: DeckIcon, color: Color, size: Dp = 26.dp, filled: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color,
                Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.8f, cap = StrokeCap.Round)
            when (icon) {
                DeckIcon.GUIDE -> {
                    drawRoundRect(color, Offset(3f, 4f), Size(18f, 16f), CornerRadius(1.5f), style = stroke)
                    line(8f, 4f, 8f, 20f); line(3f, 9f, 21f, 9f); line(3f, 14f, 21f, 14f)
                }
                DeckIcon.CHANNELS -> for (y in listOf(6f, 12f, 18f)) {
                    drawCircle(color, 1f, Offset(4f, y)); line(8f, y, 21f, y)
                }
                DeckIcon.FAVORITE -> {
                    val path = Path()
                    repeat(10) { index ->
                        val angle = -Math.PI / 2 + index * Math.PI / 5
                        val radius = if (index % 2 == 0) 9.5 else 4.3
                        val x = (12 + cos(angle) * radius).toFloat()
                        val y = (12 + sin(angle) * radius).toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close(); drawPath(path, color, style = if (filled) Fill else stroke)
                }
                DeckIcon.CONTROLS -> {
                    val path = Path().apply { moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close() }
                    drawPath(path, color, style = stroke)
                }
                DeckIcon.DETAILS -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawCircle(color, 1f, Offset(12f, 7f)); line(12f, 11f, 12f, 17f)
                }
                DeckIcon.APPEARANCE -> for ((y, x) in listOf(6f to 8f, 12f to 16f, 18f to 10f)) {
                    line(3f, y, x - 2.5f, y); line(x + 2.5f, y, 21f, y)
                    drawCircle(color, 2.5f, Offset(x, y), style = stroke)
                }
                DeckIcon.PREVIOUS -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
                DeckIcon.NEXT -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
                DeckIcon.MORE -> for (x in listOf(5f, 12f, 19f)) drawCircle(color, 1.8f, Offset(x, 12f))
                DeckIcon.BACK -> {
                    line(14f, 4f, 21f, 4f); line(21f, 4f, 21f, 20f); line(21f, 20f, 14f, 20f)
                    line(3f, 12f, 15f, 12f); line(3f, 12f, 7f, 8f); line(3f, 12f, 7f, 16f)
                }
            }
        }
    }
}