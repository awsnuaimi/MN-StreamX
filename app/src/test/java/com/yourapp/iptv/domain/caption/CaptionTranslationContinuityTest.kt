package com.yourapp.iptv.domain.caption

import org.junit.Assert.*
import org.junit.Test

class CaptionTranslationContinuityTest {
    @Test fun retryCadenceBacksOffWithBoundedWindowsAndRecovers() {
        val circuit = CaptionTranslationContinuity()
        assertTrue(circuit.shouldTranslate())
        for (round in 1..5) {
            circuit.deadline()
            assertEquals((1 shl round), circuit.deferredWindows)
            repeat(1 shl round) { assertFalse(circuit.shouldTranslate()) }
            assertTrue(circuit.shouldTranslate())
        }
        circuit.deadline()
        assertEquals(32, circuit.deferredWindows)
        circuit.success()
        assertEquals(0, circuit.consecutiveDeadlines)
        assertTrue(circuit.shouldTranslate())
    }
    @Test fun successfulButSlowTextInferencePacesLaterWindows() {
        val circuit = CaptionTranslationContinuity()
        circuit.success(5600, 2500)
        assertEquals(2, circuit.deferredWindows)
        repeat(2) { assertFalse(circuit.shouldTranslate()) }
        assertTrue(circuit.shouldTranslate())
        circuit.success(1100, 2500)
        assertEquals(0, circuit.deferredWindows)
        assertTrue(circuit.shouldTranslate())
    }
    @Test fun sourceCaptionsAreNeverPassedOffAsTranslatedText() {
        val circuit = CaptionTranslationContinuity()
        assertEquals("[EN · untranslated] Good morning", circuit.untranslated(" Good morning ", "en"))
        assertEquals("[AUTO · untranslated] Welcome", circuit.untranslated("Welcome", "?!"))
    }
}