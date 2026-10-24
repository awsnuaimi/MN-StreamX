package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class HomeTvPaletteTest {
    @Test fun nightModeIsTrulyBlackRegardlessOfThemeSetting() {
        val dark = HomeTvPalettes.forMode(isDark = true, isNight = true)
        val light = HomeTvPalettes.forMode(isDark = false, isNight = true)
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.Black, dark.sidebar)
        assertEquals(dark, light)
        assertEquals(Color.White, dark.text)
    }

    @Test fun switchingNightOffRestoresTheSelectedThemePalette() {
        assertNotEquals(Color.Black, HomeTvPalettes.forMode(isDark = true).background)
        assertNotEquals(HomeTvPalettes.forMode(isDark = true),
            HomeTvPalettes.forMode(isDark = false))
    }
}