package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionStage
import org.junit.Assert.*
import org.junit.Test

class CaptionThroughputTest {
    private fun window(start: Long, millis: Int = 5000) = AudioWindow(FloatArray(millis * 16), start * 1000)
    @Test fun oneWarmupDoesNotTriggerTheSustainedSlowCounter() {
        val policy = CaptionThroughput()
        assertNull(policy.assess(window(0), 7000, Long.MIN_VALUE))
        assertNull(policy.assess(window(5000), 7000, Long.MIN_VALUE))
        val failure = policy.assess(window(10000), 7000, Long.MIN_VALUE)
        assertEquals(CaptionStage.THROUGHPUT, failure?.stage)
        assertEquals(7000L, failure?.elapsedMillis); assertEquals(6000L, failure?.budgetMillis)
    }
    @Test fun shortUtterancesSeparatedBySilenceUseTheRealArrivalCadence() {
        val policy = CaptionThroughput()
        for (start in listOf(0L, 5000L, 10000L, 15000L)) {
            assertNull(policy.assess(window(start, 2000), 4000, Long.MIN_VALUE))
        }
    }
    @Test fun aSingleExcessivelyLongWarmupStillStops() {
        val failure = CaptionThroughput().assess(window(0), 10001, Long.MIN_VALUE)
        assertEquals(CaptionStage.THROUGHPUT, failure?.stage); assertEquals(10000L, failure?.budgetMillis)
    }
    @Test fun anOutdatedResultIsRejectedAfterInferenceAsWellAsBeforeIt() {
        val failure = CaptionThroughput().assess(window(0), 1000, 14000000)
        assertEquals(CaptionStage.LAG, failure?.stage); assertEquals(9000L, failure?.elapsedMillis)
    }
    @Test fun aFastWindowResetsTheSlowStreak() {
        val policy = CaptionThroughput()
        assertNull(policy.assess(window(0), 7000, Long.MIN_VALUE))
        assertNull(policy.assess(window(5000), 7000, Long.MIN_VALUE))
        assertNull(policy.assess(window(10000), 1000, Long.MIN_VALUE))
        assertNull(policy.assess(window(15000), 7000, Long.MIN_VALUE))
    }
    @Test fun duplicateTimestampsDoNotGrantAnArtificiallyLongBudget() {
        val policy = CaptionThroughput()
        assertNull(policy.assess(window(0, 2000), 4000, Long.MIN_VALUE))
        assertNull(policy.assess(window(0, 2000), 4000, Long.MIN_VALUE))
        assertNotNull(policy.assess(window(0, 2000), 4000, Long.MIN_VALUE))
    }
}