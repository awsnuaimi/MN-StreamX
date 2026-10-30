package com.yourapp.iptv.ui.screen.home

import org.junit.Assert.*
import org.junit.Test
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveTvNumberEntryTest {
    @Test fun leadingZerosNeverConsumeTheEntryLimit() {
        val entry = LiveTvNumberEntry(); repeat(20) { entry.digit(0) }; entry.digit(1); entry.digit(2)
        assertEquals("12", entry.digits); assertEquals(12, entry.number)
    }
    @Test fun entryLengthIsBoundedAndCannotOverflowAnInteger() {
        val entry = LiveTvNumberEntry(); repeat(100_000) { entry.digit(9) }
        assertEquals(6, entry.digits.length); assertEquals(999999, entry.number)
    }
    @Test fun invalidDigitsAreRejectedAndBackspaceHandlesAnEmptyEntry() {
        val entry = LiveTvNumberEntry(); assertThrows(IllegalArgumentException::class.java) { entry.digit(10) }
        entry.erase(); entry.digit(1); entry.digit(2); entry.erase(); assertEquals("1", entry.digits)
        entry.erase(); assertFalse(entry.active); assertNull(entry.number)
    }
    @Test fun aLateTimeoutCannotClearANewerDigit() {
        val entry = LiveTvNumberEntry(); entry.digit(1); val ticket = entry.revision; entry.digit(2)
        entry.expire(ticket); assertEquals("12", entry.digits); entry.expire(entry.revision); assertFalse(entry.active)
    }
    @Test fun confirmationRequiresAnOwnedUncancelledShortPress() {
        val entry = LiveTvNumberEntry(); entry.digit(1)
        assertFalse(entry.up(23, 10, false, 500)); entry.down(23, 20, false)
        assertTrue(entry.up(23, 30, false, 500)); assertFalse(entry.awaitingRelease)
        entry.down(23, 40, false); assertFalse(entry.up(23, 50, true, 500))
    }
    @Test fun changingDigitsWhileOkIsHeldCannotPlayThePreviousTarget() {
        val entry = LiveTvNumberEntry(); entry.digit(1); entry.down(23, 0, false); entry.digit(2)
        assertFalse(entry.up(23, 10, false, 500))
    }
    @Test fun bothLongDurationAndOemRepeatEventsSuppressConfirmation() {
        val entry = LiveTvNumberEntry(); entry.digit(1); entry.down(23, 0, false)
        assertFalse(entry.up(23, 800, false, 500)); entry.down(23, 900, false); entry.down(23, 910, true)
        assertFalse(entry.up(23, 920, false, 500))
    }
    @Test fun expirationWhileOkIsHeldKeepsItsReleaseConsumedWithoutConfirmation() {
        val entry = LiveTvNumberEntry(); entry.digit(1); entry.down(23, 0, false); entry.expire(entry.revision)
        assertFalse(entry.active); assertTrue(entry.awaitingRelease)
        assertFalse(entry.up(23, 20, false, 500)); assertFalse(entry.awaitingRelease)
    }
    @Test fun cancellationClearsTheTextAndAnyPendingConfirmation() {
        val entry = LiveTvNumberEntry(); entry.digit(1); entry.down(23, 0, false); entry.clear()
        assertFalse(entry.active); assertFalse(entry.awaitingRelease); assertFalse(entry.up(23, 1, false, 500))
    }
    @Test fun clearingAnIdleEntryDoesNotPublishSnapshotChanges() {
        val entry = LiveTvNumberEntry(); val initial = entry.revision
        repeat(100) { entry.clear() }; assertEquals(initial, entry.revision)
        entry.digit(1); entry.clear(); val cleared = entry.revision
        entry.clear(); assertEquals(cleared, entry.revision)
    }

}