package com.yourapp.iptv.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardLayoutTest {
    @Test fun compactScreenPreservesReadableTileWidth() {
        val metrics = HomeDashboardLayout.forWidth(420)
        assertTrue(metrics.compact)
        assertEquals(132, metrics.actionWidth)
        assertEquals(192, metrics.channelWidth)
        assertEquals(42, metrics.logoSize)
    }

    @Test fun breakpointsAreDeterministic() {
        assertTrue(HomeDashboardLayout.forWidth(559).compact)
        assertFalse(HomeDashboardLayout.forWidth(560).compact)
        assertEquals(160, HomeDashboardLayout.forWidth(560).actionWidth)
        assertEquals(160, HomeDashboardLayout.forWidth(919).actionWidth)
        assertEquals(186, HomeDashboardLayout.forWidth(920).actionWidth)
    }

    @Test fun dimensionsGrowWithoutExceedingFixedLimits() {
        val sizes = listOf(0, 320, 559, 560, 800, 919, 920, 1920, 3840)
            .map(HomeDashboardLayout::forWidth)
        assertTrue(sizes.zipWithNext().all { (a, b) ->
            a.actionWidth <= b.actionWidth &&
                a.channelWidth <= b.channelWidth && a.logoSize <= b.logoSize
        })
        assertTrue(sizes.all { it.actionHeight >= 64 && it.channelWidth <= 272 })
    }

    @Test fun everyChannelCardIncludesTheReferenceArtworkAndCaptionRatio() {
        for (width in listOf(320, 559, 560, 919, 920, 1920)) {
            assertEquals(20f / 13f,
                HomeDashboardLayout.forWidth(width).channelAspectRatio, 0.001f)
        }
    }
}