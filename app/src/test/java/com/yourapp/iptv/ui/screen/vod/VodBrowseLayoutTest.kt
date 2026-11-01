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

    @Test fun heroIsBoundedAndGrowsOnlyWhenSpaceAllows() {
        assertEquals(156f, vodMovieHeroHeightDp(320f), 0.001f)
        assertEquals(156f, vodMovieHeroHeightDp(Float.NaN), 0.001f)
        assertEquals(184f, vodMovieHeroHeightDp(480f), 0.001f)
        assertEquals(184f, vodMovieHeroHeightDp(759.9f), 0.001f)
        assertEquals(208f, vodMovieHeroHeightDp(760f), 0.001f)
        assertEquals(208f, vodMovieHeroHeightDp(4000f), 0.001f)
    }

    @Test fun wideScreensScalePreviewWithoutStarvingGrid() {
        assertEquals(210f, vodSeriesPreviewWidthDp(680f)!!, 0.001f)
        assertEquals(288f, vodSeriesPreviewWidthDp(960f)!!, 0.001f)
        assertEquals(340f, vodSeriesPreviewWidthDp(1400f)!!, 0.001f)
        assertEquals(340f, vodSeriesPreviewWidthDp(5000f)!!, 0.001f)
    }
}