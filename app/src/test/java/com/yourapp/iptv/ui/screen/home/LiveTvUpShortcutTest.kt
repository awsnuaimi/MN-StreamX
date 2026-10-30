package com.yourapp.iptv.ui.screen.home

import org.junit.Assert.*
import org.junit.Test

class LiveTvUpShortcutTest {
    private val start = LiveTvUpShortcut.Result.START
    private val double = LiveTvUpShortcut.Result.DOUBLE

    @Test fun twoReleasedPressesTriggerOnceWithoutDelayingTheFirst() {
        val gesture = LiveTvUpShortcut()
        assertEquals(start, gesture.down(1000, false, 1))
        gesture.up(1050, false, 1)
        assertEquals(double, gesture.down(1250, false, 1))
        gesture.up(1300, false, 1)
        assertEquals(start, gesture.down(1350, false, 1))
    }

    @Test fun repeatsAndLongHoldsCannotBecomeADoublePress() {
        val gesture = LiveTvUpShortcut()
        gesture.down(1000, false, 1)
        assertEquals(LiveTvUpShortcut.Result.NORMAL, gesture.down(1150, true, 1))
        gesture.up(1200, false, 1)
        assertEquals(start, gesture.down(1250, false, 1))
        gesture.up(1700, false, 1)
        assertEquals(start, gesture.down(1750, false, 1))
    }

    @Test fun aReleaseIsRequiredAndCanceledPressesAreDiscarded() {
        val gesture = LiveTvUpShortcut()
        gesture.down(1000, false, 1)
        assertEquals(start, gesture.down(1100, false, 1))
        gesture.up(1150, true, 1)
        assertEquals(start, gesture.down(1200, false, 1))
    }

    @Test fun separateDevicesAndExpiredOrReversedTimestampsDoNotPair() {
        val gesture = LiveTvUpShortcut()
        gesture.down(1000, false, 1); gesture.up(1050, false, 1)
        assertEquals(start, gesture.down(1100, false, 2))
        gesture.up(1150, false, 2)
        assertEquals(start, gesture.down(1450, false, 2))
        gesture.up(1500, false, 2)
        assertEquals(start, gesture.down(1400, false, 2))
    }

    @Test fun categoryStripCanFinishASequenceButCannotStartOne() {
        val gesture = LiveTvUpShortcut()
        assertEquals(LiveTvUpShortcut.Result.NORMAL, gesture.down(1000, false, 1, canStart = false))
        gesture.down(1050, false, 1); gesture.up(1100, false, 1)
        assertEquals(double, gesture.down(1200, false, 1, canStart = false))
        assertEquals(LiveTvUpShortcut.Result.NORMAL, gesture.down(1250, false, 1, canStart = false))
    }

    @Test fun resettingOnOtherKeysOrFocusLossPreventsAnAccidentalJump() {
        val gesture = LiveTvUpShortcut()
        gesture.down(1000, false, 1); gesture.up(1050, false, 1); gesture.reset()
        assertEquals(start, gesture.down(1100, false, 1))
    }
}