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

@Composable
internal fun HomeSidebarIcon(section: HomeSection, color: Color) {
    when (section) {
        HomeSection.FAVORITES -> PlayerDeckIcon(DeckIcon.FAVORITE, color)
        HomeSection.GUIDE -> PlayerDeckIcon(DeckIcon.GUIDE, color)
        HomeSection.SETTINGS, HomeSection.APPEARANCE -> PlayerDeckIcon(DeckIcon.APPEARANCE, color)
        else -> Canvas(Modifier.size(26.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val stroke = Stroke(1.8f)
                when (section) {
                    HomeSection.LIVE -> {
                        drawRoundRect(color, Offset(2f, 4f), Size(20f, 14f), CornerRadius(2f), style = stroke)
                        drawLine(color, Offset(8f, 22f), Offset(16f, 22f), 1.8f)
                        drawLine(color, Offset(12f, 18f), Offset(12f, 22f), 1.8f)
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