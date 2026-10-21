package com.yourapp.iptv.ui.presentation

import org.junit.Assert.*
import org.junit.Test

class HomeGridRestorePolicyTest {
    @Test fun preservesTheAnchorAndOffsetWhenTheFocusedCardIsInTheViewport() {
        assertEquals(HomeGridTarget(30, 12), HomeGridRestorePolicy.target(30, 40, 3, 4, 12, 3))
    }
    @Test fun changedColumnsResetThePixelOffsetAndAlignToTheNewRow() {
        assertEquals(HomeGridTarget(28, 0), HomeGridRestorePolicy.target(30, 37, 4, 4, 90, 3))
    }
    @Test fun anOffscreenFocusTakesPriorityOverAnObsoleteAnchor() {
        assertEquals(HomeGridTarget(39, 0), HomeGridRestorePolicy.target(0, 40, 3, 4, 10, 3))
        assertEquals(HomeGridTarget(0, 0), HomeGridRestorePolicy.target(39, 0, 3, 4, 10, 3))
    }
    @Test fun missingAnchorAndNarrowScreensRemainSafe() {
        assertEquals(HomeGridTarget(9, 0), HomeGridRestorePolicy.target(-1, 9, 1, 3, 99, 1))
        assertEquals(HomeGridTarget(0, 0), HomeGridRestorePolicy.target(-1, -1, 0, 0, -99, 0))
    }
}