package com.yourapp.iptv.ui.screen.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsIcon(category: SettingsCategory, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(25.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f)
            when (category) {
                SettingsCategory.ACCOUNTS -> {
                    drawCircle(color, 3.5f, Offset(10f, 7f), style = stroke)
                    drawArc(color, 180f, 180f, false, Offset(2f, 12f), Size(16f, 15f), style = stroke)
                    drawArc(color, -70f, 140f, false, Offset(14f, 3f), Size(7f, 8f), style = stroke)
                    drawLine(color, Offset(19f, 14f), Offset(22f, 20f), 1.7f)
                }
                SettingsCategory.APPEARANCE -> {
                    val path = Path().apply { moveTo(21f, 12f);cubicTo(22f, 2f, 8f, 0f, 3f, 7f)
                        cubicTo(-2f, 16f, 8f, 25f, 13f, 20f);cubicTo(17f, 15f, 11f, 14f, 16f, 14f)
                        cubicTo(20f, 15f, 21f, 14f, 21f, 12f);close() }
                    drawPath(path, color, style = stroke)
                    listOf(7f to 9f, 11f to 5f, 16f to 7f).forEach { (x, y) -> drawCircle(color, 1.2f, Offset(x, y)) }
                }
                SettingsCategory.PLAYBACK -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawPath(Path().apply { moveTo(10f, 7f);lineTo(17f, 12f);lineTo(10f, 17f);close() }, color, style = stroke)
                }
                SettingsCategory.CAPTIONS -> {
                    drawRoundRect(color, Offset(2f, 4f), Size(20f, 16f), CornerRadius(2f), style = stroke)
                    drawLine(color, Offset(6f, 10f), Offset(12f, 10f), 1.7f)
                    drawLine(color, Offset(6f, 15f), Offset(18f, 15f), 1.7f)
                }
                SettingsCategory.PARENTAL -> {
                    drawPath(Path().apply { moveTo(12f, 2f);lineTo(21f, 6f);lineTo(20f, 14f)
                        quadraticTo(18f, 20f, 12f, 23f);quadraticTo(6f, 20f, 4f, 14f)
                        lineTo(3f, 6f);close() }, color, style = stroke)
                    drawLine(color, Offset(12f, 8f), Offset(12f, 16f), 1.7f)
                }
                SettingsCategory.DATA -> {
                    drawOval(color, Offset(3f, 3f), Size(18f, 6f), style = stroke)
                    drawLine(color, Offset(3f, 6f), Offset(3f, 19f), 1.7f)
                    drawLine(color, Offset(21f, 6f), Offset(21f, 19f), 1.7f)
                    drawArc(color, 0f, 180f, false, Offset(3f, 15f), Size(18f, 7f), style = stroke)
                    drawArc(color, 0f, 180f, false, Offset(3f, 9f), Size(18f, 7f), style = stroke)
                }
                SettingsCategory.UPDATES -> {
                    drawArc(color, 35f, 290f, false, Offset(3f, 3f), Size(18f, 18f), style = stroke)
                    drawLine(color, Offset(22f, 3f), Offset(21f, 10f), 1.7f)
                    drawLine(color, Offset(21f, 10f), Offset(15f, 9f), 1.7f)
                }
                SettingsCategory.HELP -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawArc(color, 180f, 250f, false, Offset(8f, 6f), Size(7f, 7f), style = stroke)
                    drawLine(color, Offset(12f, 12f), Offset(12f, 15f), 1.7f)
                    drawCircle(color, 1f, Offset(12f, 18f))
                }
            }
        }
    }
}

@Composable
internal fun SettingsOptionIcon(kind: String, color: Color) {
    Canvas(Modifier.size(25.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f)
            when (kind) {
                "settings-language" -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawOval(color, Offset(8f, 3f), Size(8f, 18f), style = stroke)
                    drawLine(color, Offset(3f, 12f), Offset(21f, 12f), 1.7f)
                }
                "settings-night" -> drawPath(Path().apply {
                    moveTo(18f, 3f);cubicTo(5f, 0f, 0f, 18f, 12f, 21f)
                    cubicTo(17f, 23f, 22f, 18f, 22f, 15f);cubicTo(12f, 20f, 9f, 7f, 18f, 3f)
                }, color, style = stroke)
                "settings-display", "settings-channel-layout", "settings-columns" -> repeat(2) { y -> repeat(2) { x ->
                    drawRoundRect(color, Offset(3f + 10f * x, 3f + 10f * y), Size(7f, 7f), CornerRadius(1f), style = stroke)
                } }
                "settings-player-text" -> {
                    drawLine(color, Offset(2f, 20f), Offset(8f, 4f), 1.8f);drawLine(color, Offset(8f, 4f), Offset(14f, 20f), 1.8f)
                    drawLine(color, Offset(4f, 14f), Offset(12f, 14f), 1.8f)
                    drawLine(color, Offset(16f, 20f), Offset(19f, 11f), 1.8f);drawLine(color, Offset(19f, 11f), Offset(22f, 20f), 1.8f)
                    drawLine(color, Offset(17f, 17f), Offset(21f, 17f), 1.8f)
                }
                "settings-advanced" -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.7f, Offset(it, 12f)) }
                "settings-logos" -> {
                    drawRoundRect(color, Offset(2f, 3f), Size(20f, 18f), CornerRadius(2f), style = stroke)
                    drawCircle(color, 2f, Offset(16f, 8f), style = stroke)
                    drawPath(Path().apply { moveTo(3f, 19f);lineTo(9f, 11f);lineTo(15f, 19f);lineTo(19f, 14f);lineTo(22f, 18f) }, color, style = stroke)
                }
                "settings-browse-restore" -> {
                    drawArc(color, 30f, 290f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    drawLine(color, Offset(2f, 4f), Offset(3f, 10f), 1.7f);drawLine(color, Offset(3f, 10f), Offset(9f, 9f), 1.7f)
                }
                else -> {
                    drawRoundRect(color, Offset(2f, 4f), Size(20f, 16f), CornerRadius(2f), style = stroke)
                    drawLine(color, Offset(8f, 4f), Offset(8f, 20f), 1.7f)
                }
            }
        }
    }
}