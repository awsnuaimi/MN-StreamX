package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTvPaletteTest {
    @Test fun nightModeIsPureBlackRegardlessOfStoredTheme() {
        val dark = HomeTvPalettes.forMode(isDark = true, isNight = true)
        val light = HomeTvPalettes.forMode(isDark = false, isNight = true)
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.Black, dark.sidebar)
        assertEquals(dark, light)
        assertEquals(Color.White, dark.text)
    }

    @Test fun separateScrimsPreserveReadableTextAndArtwork() {
        for (night in listOf(false, true)) for (dark in listOf(false, true)) {
            val colors = HomeTvPalettes.forMode(dark, night)
            assertEquals(2, colors.cardOverlay.size)
            assertEquals(0f, colors.cardOverlay.first().alpha, 0.001f)
            assertTrue(colors.cardOverlay.last().alpha > 0.5f)
            assertTrue(colors.heroOverlay.maxOf { it.alpha } >=
                colors.cardOverlay.maxOf { it.alpha })
            assertTrue(colors.cardFallbackGradient.size >= 2)
        }
    }

    @Test fun liveBadgeRemainsRedInAllModes() {
        for (night in listOf(false, true)) for (dark in listOf(false, true)) {
            val badge = HomeTvPalettes.forMode(dark, night).liveBadge
            assertTrue(badge.red > 0.6f && badge.green < 0.4f)
        }
    }

    @Test fun switchingNightOffRestoresPreviouslySelectedTheme() {
        assertNotEquals(Color.Black, HomeTvPalettes.forMode(isDark = true).background)
        assertNotEquals(HomeTvPalettes.forMode(isDark = true),
            HomeTvPalettes.forMode(isDark = false))
    }
}