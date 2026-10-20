package com.yourapp.iptv.ui.presentation

import org.junit.Assert.*
import org.junit.Test

class PlayerTileLayoutTest {
    @Test fun logoHeaderAndChannelNameFitWithoutSharingSpaceWithThePlayingBadge() {
        for (scale in listOf(0.9f, 1f, 1.15f, 1.5f, 2f, 4f)) {
            val used = 32f + 18f * scale + 22f * scale + 8f + 16f
            assertTrue(PlayerTileLayout.height(true, scale) >= used - 0.001f)
        }
    }
    @Test fun hidingLogosStillReservesBothTextSlotsAndASensibleShortcutHeight() {
        for (scale in listOf(0.9f, 1f, 1.15f, 2f)) {
            assertTrue(PlayerTileLayout.height(false, scale) >= 40f * scale + 20f)
            assertTrue(PlayerTileLayout.height(false, scale) >= 88f)
        }
    }
    @Test fun invalidScaleHasAFiniteFallbackAndAccessibilityScaleIsPreserved() {
        assertEquals(1f, PlayerTileLayout.fontScale(Float.NaN), 0f)
        assertEquals(1f, PlayerTileLayout.fontScale(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0.8f, PlayerTileLayout.fontScale(-1f), 0f)
        assertEquals(4f, PlayerTileLayout.fontScale(4f), 0f)
    }
}