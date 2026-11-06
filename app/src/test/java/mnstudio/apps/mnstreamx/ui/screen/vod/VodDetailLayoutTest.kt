package mnstudio.apps.mnstreamx.ui.screen.vod

import org.junit.Assert.*
import org.junit.Test

class VodDetailLayoutTest {
    @Test fun compactWidthsUseFullAvailableSpace() {
        assertFalse(VodDetailLayout.split(719.9f))
        assertFalse(VodDetailLayout.split(0f))
        assertFalse(VodDetailLayout.split(Float.NaN))
        assertFalse(VodDetailLayout.split(Float.POSITIVE_INFINITY))
        assertEquals(1f, VodDetailLayout.panelFraction(600f), 0f)
    }

    @Test fun wideTvUsesTwoColumnsWithSafeMargin() {
        assertTrue(VodDetailLayout.split(720f))
        assertTrue(VodDetailLayout.split(960f))
        assertEquals(.94f, VodDetailLayout.panelFraction(960f), 0f)
    }
}