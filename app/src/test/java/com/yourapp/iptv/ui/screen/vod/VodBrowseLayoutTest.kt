package com.yourapp.iptv.ui.screen.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodBrowseLayoutTest {
    @Test fun compactScreensKeepThePosterGridFullWidth() {
        assertNull(vodSeriesPreviewWidthDp(0f))
        assertNull(vodSeriesPreviewWidthDp(540f))
        assertNull(vodSeriesPreviewWidthDp(679.9f))
        assertNull(vodSeriesPreviewWidthDp(Float.NaN))
    }

    @Test fun wideScreensScalePreviewWithoutStarvingGrid() {
        assertEquals(210f, vodSeriesPreviewWidthDp(680f)!!, 0.001f)
        assertEquals(288f, vodSeriesPreviewWidthDp(960f)!!, 0.001f)
        assertEquals(340f, vodSeriesPreviewWidthDp(1400f)!!, 0.001f)
        assertEquals(340f, vodSeriesPreviewWidthDp(5000f)!!, 0.001f)
    }
}