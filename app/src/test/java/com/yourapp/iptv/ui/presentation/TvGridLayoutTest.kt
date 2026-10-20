package com.yourapp.iptv.ui.presentation

import org.junit.Assert.*
import org.junit.Test

class TvGridLayoutTest {
    @Test fun completeRowsFillTheViewportAcrossTvSizesWithoutPartialRows() {
        for (viewport in listOf(240f, 320f, 405f, 480f, 540f, 720f)) {
            for (scale in listOf(1f, 1.15f, 1.5f, 2f)) {
                val rows = TvGridLayout.rows(viewport, 96f * scale)
                val filled = rows.count * rows.height + (rows.count - 1) * 12f + 8f
                assertEquals(viewport, filled, 0.01f)
                assertTrue(rows.count >= 1)
                if (rows.count > 1) assertTrue(rows.height >= 96f * scale - 0.01f)
            }
        }
    }
    @Test fun idleScrollKeepsTheFocusedFirstRowOrAdvancesPastAPartialPreviousRow() {
        assertEquals(6, TvGridLayout.snapTarget(7, 22, 8, 3, 73))
        assertEquals(9, TvGridLayout.snapTarget(7, 22, 14, 3, 73))
        assertEquals(6, TvGridLayout.rowStart(8, 3))
        assertEquals(8, TvGridLayout.rowStart(11, 4))
    }
    @Test fun roundingNoiseAndEmptyListsDoNotTriggerRepeatedSnapping() {
        assertNull(TvGridLayout.snapTarget(0, 2, 4, 3, 73))
        assertNull(TvGridLayout.snapTarget(0, 100, -1, 3, 0))
        assertEquals(72, TvGridLayout.snapTarget(72, 8, -1, 3, 73))
    }
}