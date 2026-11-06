package mnstudio.apps.mnstreamx.ui.screen.vod

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal enum class VodRailGlyph { HOME, MOVIE, SERIES }

/** Compact line icons; no font glyph or additional dependency required. */
@Composable
internal fun VodRailIcon(glyph: VodRailGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pen = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) {
            drawLine(color, Offset(x * w, y * h), Offset(x2 * w, y2 * h), pen.width,
                cap = StrokeCap.Round)
        }
        when (glyph) {
            VodRailGlyph.HOME -> {
                val roof = Path().apply {
                    moveTo(.12f * w, .45f * h)
                    lineTo(.50f * w, .14f * h)
                    lineTo(.88f * w, .45f * h)
                }
                drawPath(roof, color, style = pen)
                val walls = Path().apply {
                    moveTo(.23f * w, .43f * h)
                    lineTo(.23f * w, .86f * h)
                    lineTo(.77f * w, .86f * h)
                    lineTo(.77f * w, .43f * h)
                }
                drawPath(walls, color, style = pen)
                drawRect(color, Offset(.44f * w, .63f * h), Size(.12f * w, .23f * h), style = pen)
            }
            VodRailGlyph.MOVIE -> {
                drawRoundRect(color, Offset(.10f * w, .27f * h), Size(.80f * w, .61f * h),
                    androidx.compose.ui.geometry.CornerRadius(.08f * w), style = pen)
                line(.13f, .49f, .87f, .49f)
                line(.18f, .28f, .30f, .49f)
                line(.46f, .28f, .58f, .49f)
                line(.74f, .28f, .84f, .49f)
            }
            VodRailGlyph.SERIES -> {
                drawRoundRect(color, Offset(.18f * w, .10f * h), Size(.64f * w, .80f * h),
                    androidx.compose.ui.geometry.CornerRadius(.07f * w), style = pen)
                line(.32f, .32f, .68f, .32f)
                line(.32f, .49f, .68f, .49f)
                line(.32f, .66f, .58f, .66f)
            }
        }
    }
}