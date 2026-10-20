package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.screen.player.deck.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun HomeSidebarIcon(section: HomeSection, color: Color) {
    when (section) {
        HomeSection.FAVORITES -> PlayerDeckIcon(DeckIcon.FAVORITE, color)
        HomeSection.GUIDE -> PlayerDeckIcon(DeckIcon.GUIDE, color)
        else -> Canvas(Modifier.size(26.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val stroke = Stroke(1.8f)
                when (section) {
                    HomeSection.LIVE -> {
                        drawRoundRect(color, Offset(2f, 4f), Size(20f, 14f), CornerRadius(2f), style = stroke)
                        drawLine(color, Offset(8f, 22f), Offset(16f, 22f), 1.8f)
                        drawLine(color, Offset(12f, 18f), Offset(12f, 22f), 1.8f)
                    }
                    HomeSection.SETTINGS -> {
                        drawCircle(color, 6.5f, Offset(12f, 12f), style = stroke)
                        drawCircle(color, 2.5f, Offset(12f, 12f), style = stroke)
                        repeat(8) { index ->
                            val angle = index * Math.PI / 4
                            drawLine(color, Offset(12f + 7f * cos(angle).toFloat(), 12f + 7f * sin(angle).toFloat()),
                                Offset(12f + 10f * cos(angle).toFloat(), 12f + 10f * sin(angle).toFloat()), 2.5f)
                        }
                    }
                    HomeSection.APPEARANCE -> {
                        val palette = Path().apply {
                            moveTo(21f, 12f); cubicTo(21f, 4f, 13f, 1f, 7f, 4f)
                            cubicTo(0f, 7f, 1f, 18f, 9f, 21f)
                            cubicTo(15f, 23f, 18f, 19f, 14f, 16f)
                            cubicTo(12f, 13f, 21f, 17f, 21f, 12f); close()
                        }
                        drawPath(palette, color, style = stroke)
                        listOf(7f to 10f, 11f to 6f, 16f to 8f).forEach { (x, y) -> drawCircle(color, 1.5f, Offset(x, y)) }
                    }
                    HomeSection.ORGANIZE -> {
                        val path = Path().apply {
                            moveTo(2f, 6f); lineTo(9f, 6f); lineTo(12f, 9f); lineTo(22f, 9f)
                            lineTo(22f, 21f); lineTo(2f, 21f); close()
                        }
                        drawPath(path, color, style = stroke)
                    }
                    HomeSection.SEARCH -> {
                        drawCircle(color, 7f, Offset(10f, 10f), style = stroke)
                        drawLine(color, Offset(15f, 15f), Offset(22f, 22f), 1.8f)
                    }
                    HomeSection.REFRESH -> {
                        drawArc(color, 40f, 290f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                        drawLine(color, Offset(21f, 4f), Offset(20f, 10f), 1.8f)
                        drawLine(color, Offset(20f, 10f), Offset(14f, 8f), 1.8f)
                    }
                    else -> Unit
                }
            }
        }
    }
}