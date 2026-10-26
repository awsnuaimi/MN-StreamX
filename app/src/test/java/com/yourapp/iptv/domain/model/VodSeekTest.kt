package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class VodSeekTest {
    @Test fun forwardAndRewindAreClampedToTheMediaBounds() {
        assertEquals(0L,VodSeek.target(5000,20000,-10000))
        assertEquals(20000L,VodSeek.target(15000,20000,10000))
        assertEquals(10000L,VodSeek.target(-100,20000,10000))
    }
    @Test fun unknownDurationDoesNotInventASeekableTimeline() {
        assertNull(VodSeek.target(1000,-9223372036854775807,10000))
        assertNull(VodSeek.target(1000,0,10000))
    }
    @Test fun hostileValuesCannotOverflowTheSeekPosition() {
        assertEquals(Long.MAX_VALUE,VodSeek.target(Long.MAX_VALUE - 1,Long.MAX_VALUE,Long.MAX_VALUE))
        assertEquals(0L,VodSeek.target(Long.MAX_VALUE,Long.MAX_VALUE,Long.MIN_VALUE))
    }
    @Test fun positionLabelsShowSecondsAndHoursIndependentlyOfDeviceLocale() {
        assertEquals("01:05",VodSeek.time(65000));assertEquals("2:01:05",VodSeek.time(7265000))
        assertEquals("–:––",VodSeek.time(-1))
    }
}