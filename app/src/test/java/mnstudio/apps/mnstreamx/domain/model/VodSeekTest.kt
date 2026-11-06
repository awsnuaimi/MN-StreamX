package mnstudio.apps.mnstreamx.domain.model

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
    @Test fun shortPressAndHeldPressesIncreaseWithoutFloodingThePlayer() {
        assertEquals(10_000L,VodSeek.repeatStepMs(0))
        assertEquals(10_000L,VodSeek.repeatStepMs(3))
        assertEquals(30_000L,VodSeek.repeatStepMs(4))
        assertEquals(30_000L,VodSeek.repeatStepMs(11))
        assertEquals(60_000L,VodSeek.repeatStepMs(12))
        assertEquals(60_000L,VodSeek.repeatStepMs(50))
        assertEquals(listOf(0,1,2,3,4,7,10,12,17),
            (0..20).filter(VodSeek::shouldDispatchRepeat))
    }
    @Test fun acceleratedStepsClampAtBothEnds() {
        assertEquals(120_000L,VodSeek.target(95_000,120_000,VodSeek.repeatStepMs(15)))
        assertEquals(0L,VodSeek.target(20_000,120_000,-VodSeek.repeatStepMs(15)))
        assertNull(VodSeek.target(1_000,-1,VodSeek.repeatStepMs(15)))
    }
    @Test fun positionLabelsShowSecondsAndHoursIndependentlyOfDeviceLocale() {
        assertEquals("01:05",VodSeek.time(65000));assertEquals("2:01:05",VodSeek.time(7265000))
        assertEquals("–:––",VodSeek.time(-1))
    }
}