package com.yourapp.iptv.ui.screen.epg

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class EpgRemoteInputTest {
    @Test fun movesBetweenThePanesInBothLayoutDirections() {
        for (rtl in listOf(false, true)) {
            val commands = mutableListOf<EpgFocusArea>()
            val forward = if (rtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val back = if (rtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            assertTrue(handleEpgPaneKey(forward, KeyEvent.ACTION_DOWN, 0, EpgFocusArea.CHANNELS, rtl, commands::add))
            assertTrue(handleEpgPaneKey(back, KeyEvent.ACTION_DOWN, 0, EpgFocusArea.PROGRAMS, rtl, commands::add))
            assertEquals(listOf(EpgFocusArea.PROGRAMS, EpgFocusArea.CHANNELS), commands)
        }
    }

    @Test fun holdingTheButtonOrReleasingItDoesNotMoveFocusAgain() {
        val commands = mutableListOf<EpgFocusArea>()
        for ((action, repeats) in listOf(KeyEvent.ACTION_DOWN to 0, KeyEvent.ACTION_DOWN to 1,
            KeyEvent.ACTION_DOWN to 2, KeyEvent.ACTION_UP to 0)) {
            assertTrue(handleEpgPaneKey(KeyEvent.KEYCODE_DPAD_RIGHT, action, repeats,
                EpgFocusArea.CHANNELS, false, commands::add))
        }
        assertEquals(listOf(EpgFocusArea.PROGRAMS), commands)
    }

    @Test fun verticalNavigationOkAndBackAreLeftToTheNativeComponents() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)) {
            assertFalse(handleEpgPaneKey(key, KeyEvent.ACTION_DOWN, 0, EpgFocusArea.CHANNELS, false) {
                error("Unexpected interception")
            })
        }
    }

    @Test fun searchAndFilterArrowsAreNotIntercepted() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertFalse(handleEpgPaneKey(key, KeyEvent.ACTION_DOWN, 0, EpgFocusArea.FILTERS, false) {
                error("Unexpected interception")
            })
        }
    }
}