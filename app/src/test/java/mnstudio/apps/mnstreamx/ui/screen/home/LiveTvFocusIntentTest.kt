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

    private fun restored(focused: String? = null, selected: String? = null,
        adaptive: String? = null, previous: Int = 0, remaining: List<String> = keys): String? =
        LiveTvFocusIntent.restoredRowKey(focused, selected, adaptive, previous,
            remaining.size, { id -> remaining.firstOrNull { it == id } }, remaining::getOrNull)

    @Test fun aHiddenMiddleRowMovesFocusToTheNextRemainingRow() {
        assertEquals("d", restored(focused = "c", previous = 2, remaining = listOf("a", "b", "d")))
    }

    @Test fun aHiddenLastRowMovesFocusToTheNearestPreviousRow() {
        assertEquals("c", restored(focused = "d", previous = 3, remaining = listOf("a", "b", "c")))
        assertNull(restored(focused = "d", previous = 3, remaining = emptyList()))
    }

    @Test fun catalogRefreshKeepsTheActualFocusedRowInsteadOfSelection() {
        assertEquals("c", restored(focused = "c", selected = "a", adaptive = "d", previous = 2))
    }

    @Test fun queuedListEntryPrefersExplicitRestoreThenAdaptiveRow() {
        assertEquals("b", restored(selected = "b", adaptive = "d"))
        assertEquals("d", restored(adaptive = "d"))
        assertEquals("a", restored())
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