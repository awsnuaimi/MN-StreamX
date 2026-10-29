package com.yourapp.iptv.ui.screen.vod

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class VodGlyph { PLAY, PAUSE, BACK, REWIND, FORWARD, NEXT, AUDIO, SUBTITLES, SPEED, DISPLAY, RESTART, AUTO }

/** Neutral player chrome stays readable over video in every application theme. */
@Composable
internal fun VodControlButton(label: String, glyph: VodGlyph, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, value: String? = null, prominent: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val filled = enabled && (focused || prominent)
    val background by animateColorAsState(if (filled) Color.White else Color(0xE0141B25),tween(140),label = "vod-pill")
    Button(onClick,modifier.heightIn(min = if (value == null) 44.dp else 54.dp)
        .focusProperties { canFocus = enabled }.onFocusChanged { focused = it.isFocused },enabled = enabled,shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 13.dp,vertical = 8.dp),
        border = BorderStroke(if (focused) 2.dp else 1.dp,if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .13f)),
        colors = ButtonDefaults.buttonColors(containerColor = background,contentColor = if (filled) Color(0xFF111820) else Color.White,
            disabledContainerColor = Color(0xB0141B25),disabledContentColor = Color.White.copy(alpha = .36f))) {
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(if (value == null) 20.dp else 28.dp).background(
                if (filled) Color.Black.copy(alpha = .06f) else Color.White.copy(alpha = .07f),CircleShape),contentAlignment = Alignment.Center) {
                VodControlIcon(glyph,Modifier.size(if (value == null) 18.dp else 17.dp))
            }
            Column(Modifier.weight(1f,fill = false),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label,fontSize = if (value == null) 13.sp else 12.sp,maxLines = 1,overflow = TextOverflow.Ellipsis)
                value?.let { Text(it,fontSize = 10.sp,lineHeight = 12.sp,color = LocalContentColor.current.copy(alpha = .67f),
                    maxLines = 1,overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
@Composable
internal fun VodControlIcon(glyph: VodGlyph, modifier: Modifier = Modifier) {
    val ink = LocalContentColor.current
    val mirror = glyph == VodGlyph.BACK && LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = Stroke(2.dp.toPx())
        fun line(x: Float,y: Float,a: Float,b: Float) = drawLine(ink,Offset(w*(if (mirror) 1-x else x),h*y),
            Offset(w*(if (mirror) 1-a else a),h*b),stroke.width)
        fun triangle(x: Float, reverse: Boolean = false) {
            val direction = if (reverse) -1 else 1
            drawPath(Path().apply { moveTo(w*x,h*.22f);lineTo(w*(x+direction*.3f),h*.5f);lineTo(w*x,h*.78f);close() },ink)
        }
        when (glyph) {
            VodGlyph.PLAY -> triangle(.35f)
            VodGlyph.PAUSE -> {
                drawRoundRect(ink,Offset(w*.28f,h*.2f),androidx.compose.ui.geometry.Size(w*.14f,h*.6f),androidx.compose.ui.geometry.CornerRadius(w*.04f))
                drawRoundRect(ink,Offset(w*.58f,h*.2f),androidx.compose.ui.geometry.Size(w*.14f,h*.6f),androidx.compose.ui.geometry.CornerRadius(w*.04f))
            }
            VodGlyph.REWIND -> { triangle(.48f,true);triangle(.83f,true) }
            VodGlyph.FORWARD -> { triangle(.17f);triangle(.52f) }
            VodGlyph.NEXT -> { triangle(.25f);line(.8f,.22f,.8f,.78f) }
            VodGlyph.BACK -> { line(.7f,.2f,.3f,.5f);line(.3f,.5f,.7f,.8f) }
            VodGlyph.AUDIO -> {
                drawPath(Path().apply { moveTo(w*.15f,h*.4f);lineTo(w*.35f,h*.4f);lineTo(w*.55f,h*.2f)
                    lineTo(w*.55f,h*.8f);lineTo(w*.35f,h*.6f);lineTo(w*.15f,h*.6f);close() },ink,style = stroke)
                line(.72f,.35f,.85f,.5f);line(.85f,.5f,.72f,.65f)
            }
            VodGlyph.SUBTITLES -> {
                drawRoundRect(ink,Offset(w*.1f,h*.23f),androidx.compose.ui.geometry.Size(w*.8f,h*.55f),
                    androidx.compose.ui.geometry.CornerRadius(w*.08f),style = stroke)
                line(.25f,.44f,.42f,.44f);line(.57f,.44f,.75f,.44f);line(.25f,.61f,.75f,.61f)
            }
            VodGlyph.DISPLAY -> {
                drawRect(ink,Offset(w*.12f,h*.22f),androidx.compose.ui.geometry.Size(w*.76f,h*.56f),style = stroke)
                line(.35f,.9f,.65f,.9f)
            }
            VodGlyph.SPEED -> {
                drawArc(ink,180f,180f,false,Offset(w*.1f,h*.15f),androidx.compose.ui.geometry.Size(w*.8f,h*.8f),style = stroke)
                line(.5f,.65f,.75f,.32f);line(.12f,.72f,.88f,.72f)
            }
            VodGlyph.RESTART -> {
                drawArc(ink,-80f,285f,false,Offset(w*.2f,h*.2f),androidx.compose.ui.geometry.Size(w*.65f,h*.65f),style = stroke)
                line(.13f,.25f,.15f,.55f);line(.15f,.55f,.42f,.51f)
            }
            VodGlyph.AUTO -> { triangle(.16f);line(.67f,.2f,.85f,.2f);line(.85f,.2f,.85f,.8f);line(.85f,.8f,.67f,.8f) }
        }
    }
}