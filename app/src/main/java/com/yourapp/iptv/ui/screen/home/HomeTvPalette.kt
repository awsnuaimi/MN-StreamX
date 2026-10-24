package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.graphics.Color

/** Single source for Smart Home and sidebar contrasts in dark and light mode. */
internal data class HomeTvPalette(
    val background: Color,
    val sidebar: Color,
    val surface: Color,
    val focused: Color,
    val selected: Color,
    val outline: Color,
    val selectedOutline: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val logoSurface: Color,
    val heroGradient: List<Color>,
    val heroOverlay: List<Color>
)

internal object HomeTvPalettes {
    private val dark = HomeTvPalette(
        background = Color(0xFF101E33), sidebar = Color(0xFF0C1428),
        surface = Color(0xFF172B46), focused = Color(0xFF1C5272),
        selected = Color(0xFF16344D), outline = Color(0xFF2D4660),
        selectedOutline = Color(0xFF296281), text = Color.White,
        muted = Color(0xFFB6CADC), accent = Color(0xFF00D2FF),
        logoSurface = Color(0xFF0B172B),
        heroGradient = listOf(Color(0xFF163D58), Color(0xFF10192E)),
        heroOverlay = listOf(Color(0xF010192E), Color(0xB010192E), Color(0x6010192E))
    )
    private val night = HomeTvPalette(
        background = Color.Black, sidebar = Color.Black,
        surface = Color(0xFF0C0C0C), focused = Color(0xFF202020),
        selected = Color(0xFF151515), outline = Color(0xFF414141),
        selectedOutline = Color(0xFF5C5C5C), text = Color.White,
        muted = Color(0xFFC4C4C4), accent = Color(0xFF00D2FF),
        logoSurface = Color(0xFF080808),
        heroGradient = listOf(Color(0xFF111111), Color.Black),
        heroOverlay = listOf(Color(0xFA000000), Color(0xD9000000), Color(0xA0000000))
    )
    private val light = HomeTvPalette(
        background = Color(0xFFF0F4F8), sidebar = Color.White,
        surface = Color.White, focused = Color(0xFFDCEAF9),
        selected = Color(0xFFE4EEF9), outline = Color(0xFFC4D5E6),
        selectedOutline = Color(0xFF8DABCC), text = Color(0xFF16283B),
        muted = Color(0xFF52667A), accent = Color(0xFF005EB8),
        logoSurface = Color(0xFFF2F5FA),
        heroGradient = listOf(Color(0xFFE4F0FF), Color(0xFFFFFFFF)),
        heroOverlay = listOf(Color(0xFFF0F6FE), Color(0xDDF0F6FE), Color(0x88FFFFFF))
    )
    fun forMode(isDark: Boolean, isNight: Boolean = false): HomeTvPalette = when {
        isNight -> night
        isDark -> dark
        else -> light
    }
}