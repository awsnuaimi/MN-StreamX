package mnstudio.apps.mnstreamx.ui.screen.home

import org.junit.Assert.*
import org.junit.Test

class LiveTvFocusIntentTest {
    private val keys = listOf("a", "b", "c", "d")
    private fun locate(key: String) = keys.indexOf(key)
    private fun position(pending: String?, focused: String?, active: String?, count: Int = keys.size) =
        LiveTvFocusIntent.position(pending, focused, active, count, ::locate)

    @Test fun pendingMovementWinsOverBothActualFocusAndStaleUiSelection() {
        assertEquals(3, position("d", "c", "a"))
        assertEquals(2, position(null, "c", "a"))
    }

    @Test fun aRemovedRowFallsBackToTheLastStillValidFocusSource() {
        assertEquals(2, position("removed", "c", "a"))
        assertEquals(0, position("removed", "gone", "a"))
        assertEquals(0, position("removed", "gone", "invalid"))
    }

    @Test fun emptyAndSmallListsNeverGenerateAnOutOfBoundsIndex() {
        assertEquals(-1, position("d", "c", "b", 0))
        assertEquals(0, position("d", "c", "b", 1))
        assertEquals(0, position(null, null, null))
    }

    @Test fun fastScrollingIsOnlyUsedForBurstsRepeatsAndPageJumps() {
        assertFalse(LiveTvFocusIntent.fastScroll(1, repeated = false, pending = false))
        assertFalse(LiveTvFocusIntent.fastScroll(-1, repeated = false, pending = false))
        assertTrue(LiveTvFocusIntent.fastScroll(1, repeated = true, pending = false))
        assertTrue(LiveTvFocusIntent.fastScroll(-1, repeated = false, pending = true))
        assertTrue(LiveTvFocusIntent.fastScroll(8, repeated = false, pending = false))
        assertTrue(LiveTvFocusIntent.fastScroll(-8, repeated = false, pending = false))
    }
}