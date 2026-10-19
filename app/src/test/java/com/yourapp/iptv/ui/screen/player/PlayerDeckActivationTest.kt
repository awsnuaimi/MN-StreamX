package com.yourapp.iptv.ui.screen.player

import org.junit.Assert.*
import org.junit.Test

class PlayerDeckActivationTest {
    @Test fun consumesWholeOkPressWithoutForwardingReleaseToTheNativePlayer() {
        val key = PlayerDeckActivation(); var opened = 0
        assertTrue(key.handle(23, 0, 0, true) { opened++ })
        assertTrue(key.handle(23, 0, 1, false) { opened++ })
        assertTrue(key.handle(23, 1, 0, false) { opened++ })
        assertEquals(1, opened)
        assertFalse(key.handle(23, 0, 0, false) { opened++ })
    }
    @Test fun enterVariantsShowInformationWhileNativeControlsAndDialogsKeepTheirKeys() {
        for (code in listOf(23, 66, 160)) {
            val key = PlayerDeckActivation(); var opened = 0
            assertFalse(key.handle(code, 0, 0, false) { opened++ })
            assertTrue(key.handle(code, 0, 0, true) { opened++ })
            assertTrue(key.handle(code, 1, 0, false) { opened++ })
            assertEquals(1, opened)
        }
        assertFalse(PlayerDeckActivation().handle(85, 0, 0, true) { fail("Media play key intercepted") })
    }
    @Test fun repeatedPressCannotReopenOrToggleNewlyFocusedAction() {
        val key = PlayerDeckActivation(); var opened = 0
        assertTrue(key.handle(23, 0, 2, true) { opened++ })
        assertTrue(key.handle(23, 1, 0, true) { opened++ })
        assertEquals(0, opened)
        assertTrue(key.handle(23, 0, 0, true) { opened++ })
        assertEquals(1, opened)
    }
    @Test fun resetClearsALostOkReleaseWhenTheAppStops() {
        val key = PlayerDeckActivation(); var opened = 0
        key.handle(23, 0, 0, true) { opened++ }; key.reset()
        key.handle(23, 0, 0, true) { opened++ }
        assertEquals(2, opened)
    }
}