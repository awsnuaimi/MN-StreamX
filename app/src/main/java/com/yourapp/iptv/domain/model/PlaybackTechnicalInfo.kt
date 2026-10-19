package com.yourapp.iptv.domain.model

import java.util.Locale
import kotlin.math.roundToInt

data class PlaybackTechnicalInfo(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float? = null,
    val audioChannels: Int = 0
) {
    fun badges(): List<String> = buildList {
        if (width in 1..16384 && height in 1..16384) {
            val longSide = maxOf(width, height)
            val shortSide = minOf(width, height)
            add(when {
                shortSide == 2160 && longSide in setOf(3840, 4096) -> "4K"
                shortSide == 1080 && longSide == 1920 -> "FULL HD"
                shortSide == 720 && longSide == 1280 -> "HD"
                else -> "$width×$height"
            })
        }
        frameRate?.takeIf { it.isFinite() && it >= 0.1f && it <= 240 }?.let {
            val rounded = (it * 10).roundToInt() / 10f
            val label = if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString()
                else String.format(Locale.ROOT, "%.1f", rounded)
            add("$label FPS")
        }
        when (audioChannels) {
            1 -> add("MONO")
            2 -> add("STEREO")
            6 -> add("5.1")
            8 -> add("7.1")
            in 1..32 -> add("$audioChannels CH")
        }
    }
}