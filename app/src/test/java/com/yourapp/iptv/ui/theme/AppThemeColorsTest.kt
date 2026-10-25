package com.yourapp.iptv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.yourapp.iptv.ui.screen.home.HomeTvPalettes
import com.yourapp.iptv.domain.model.HomeTheme
import org.junit.Assert.*
import org.junit.Test

class AppThemeColorsTest {
    private val modes = HomeTheme.entries.flatMap { listOf(it to false, it to true) }

    @Test fun textAndStateColorsHaveReadableContrastAcrossAllModes() {
        for ((theme, night) in modes) {
            val c = AppThemeColors.forTheme(theme, night)
            val pairs = listOf(c.onBackground to c.background, c.onSurface to c.surface,
                c.onSurfaceVariant to c.surface, c.onSurfaceVariant to c.surfaceVariant,
                c.onPrimary to c.primary, c.onPrimaryContainer to c.primaryContainer,
                c.onSecondaryContainer to c.secondaryContainer, c.onTertiaryContainer to c.tertiaryContainer,
                c.onError to c.error, c.onErrorContainer to c.errorContainer,
                c.onSurface to c.primaryContainer, c.primary to c.secondaryContainer,
                c.tertiary to c.tertiaryContainer,
                AppThemeColors.favorite(c) to c.surface, AppThemeColors.warning(c) to c.surface)
            for ((foreground, background) in pairs) {
                val ratio = contrast(foreground, background)
                assertTrue("Contrast $ratio for theme=$theme/night=$night: $foreground / $background", ratio >= 4.5f)
            }
        }
    }

    @Test fun everyTonalSurfaceUsesTheCurrentPaletteWithoutAPurpleDefault() {
        for ((theme, night) in modes) {
            val c = AppThemeColors.forTheme(theme, night)
            val surfaces = listOf(c.surfaceDim, c.surfaceBright, c.surfaceContainerLowest,
                c.surfaceContainerLow, c.surfaceContainer, c.surfaceContainerHigh, c.surfaceContainerHighest)
            assertEquals(Color.Transparent, c.surfaceTint)
            for (surface in surfaces) {
                assertEquals(1f, surface.alpha, 0f)
                assertTrue(contrast(c.onSurface, surface) >= 4.5f)
                assertTrue(contrast(c.onSurfaceVariant, surface) >= 4.5f)
                if (night) {
                    assertEquals(surface.red, surface.green, 0f)
                    assertEquals(surface.green, surface.blue, 0f)
                }
            }
        }
    }

    @Test fun nightModeIsIndependentAndRestoresTheSelectedBaseTheme() {
        assertSame(AppThemeColors.forMode(true, true), AppThemeColors.forMode(false, true))
        assertEquals(Color.Black, AppThemeColors.forMode(false, true).background)
        assertEquals(Color.Black, AppThemeColors.forMode(true, true).surfaceContainerLowest)
        assertSame(AppThemeColors.forTheme(HomeTheme.RED, true), AppThemeColors.forMode(true, true))
        assertNotEquals(AppThemeColors.forTheme(HomeTheme.RED), AppThemeColors.forMode(true))
        assertNotEquals(AppThemeColors.forMode(false), AppThemeColors.forMode(true))
        assertNotEquals(Color.Black, AppThemeColors.forMode(false).background)
    }

    @Test fun theHomeAndMaterialPalettesShareTheSameSemanticRoles() {
        for ((theme, night) in modes) {
            val home = HomeTvPalettes.forTheme(theme, night)
            val app = AppThemeColors.forTheme(theme, night)
            assertEquals(app.background, home.background)
            assertEquals(app.surfaceContainerLowest, home.sidebar)
            assertEquals(app.primaryContainer, home.focused)
            assertEquals(app.onSurface, home.text)
            assertEquals(app.primary, home.accent)
            assertEquals(app.secondaryContainer, home.selected)
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}