package com.yourapp.iptv.ui.presentation

import com.yourapp.iptv.domain.model.HomeChannelLayout
import org.junit.Assert.*
import org.junit.Test

class ChannelBrowseLayoutTest {
    @Test fun densityAdaptsToWidthsFontsAndSavedPreferencesWithoutOverflow() {
        for (width in listOf(120f, 360f, 540f, 680f, 840f, 1280f)) for (scale in listOf(1f, 1.25f, 1.5f, 2f))
            for (preferred in 2..5) {
                val columns = TvGridLayout.channelColumns(width, preferred, scale, HomeChannelLayout.GRID)
                assertTrue(columns in 1..preferred)
                if (columns > 1) assertTrue(columns * 144f * scale + (columns - 1) * 8f + 8f <= width + 0.01f)
                assertEquals(1, TvGridLayout.channelColumns(width, preferred, scale, HomeChannelLayout.LIST))
            }
        assertEquals(4, TvGridLayout.channelColumns(680f, 5, 1f, HomeChannelLayout.GRID))
        assertEquals(5, TvGridLayout.channelColumns(840f, 5, 1f, HomeChannelLayout.GRID))
        assertTrue(TvGridLayout.channelColumns(680f, 5, 2f, HomeChannelLayout.GRID) < 4)
    }

    @Test fun compactRowsFillTheViewportAndScaleTheirMinimumTextSpace() {
        for (layout in HomeChannelLayout.entries) for (height in listOf(120f, 260f, 380f, 540f))
            for (scale in listOf(1f, 1.5f, 2f)) {
                val rows = TvGridLayout.channelRows(height, scale, layout)
                assertTrue(rows.count >= 1)
                assertEquals(height, rows.count * rows.height + (rows.count - 1) * 8f + 8f, 0.01f)
                if (rows.count > 1) assertTrue(rows.height >= (if (layout == HomeChannelLayout.LIST) 64f else 80f) * scale - 0.01f)
            }
    }

    @Test fun switchingGridAndListRetainsTheFocusedChannelAndDiscardsIncompatiblePixelOffsets() {
        assertEquals(HomeGridTarget(37, 0), HomeGridRestorePolicy.target(30, 37, 1, 5, 60, 5))
        assertEquals(HomeGridTarget(35, 0), HomeGridRestorePolicy.target(37, 37, 5, 4, 60, 1))
        assertEquals(HomeGridTarget(35, 12), HomeGridRestorePolicy.target(35, 37, 5, 4, 12, 5))
    }

    @Test fun guideRowsReserveSpaceForNowNextTimeAndProgressInBothLayouts() {
        for (layout in HomeChannelLayout.entries) for (height in listOf(260f, 380f, 540f))
            for (scale in listOf(1f, 1.5f, 2f)) {
                val rows = TvGridLayout.channelRows(height, scale, layout, showGuide = true)
                assertEquals(height, rows.count * rows.height + (rows.count - 1) * 8f + 8f, 0.01f)
                if (rows.count > 1) assertTrue(rows.height >= (if (layout == HomeChannelLayout.LIST) 104f else 112f) * scale - 0.01f)
            }
    }
}