package com.yourapp.iptv.domain.performance

import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PerformanceRecorderTest {
    @Test fun windowsRetain32SamplesAndUseNearestRankCompletedPercentiles() {
        val recorder = PerformanceRecorder()
        (1L..100L).forEach { recorder.recordMillis(PerformanceStage.HOME_FILTER,it) }
        val value = recorder.snapshot().single()
        assertEquals(32,value.samples);assertEquals(84L,value.p50Ms);assertEquals(99L,value.p95Ms)
        assertEquals(100L,value.maxMs);assertTrue(value.valid())
    }
    @Test fun cancelledAndFailedAttemptsDoNotDistortSuccessfulPercentiles() = runBlocking {
        var now = 0L;val recorder = PerformanceRecorder { now }
        recorder.measure(PerformanceStage.PROFILE_COMMIT) { now += 8_000_000 }
        try { recorder.measure(PerformanceStage.PROFILE_COMMIT) { now += 5_000_000;throw IOException() } } catch (_: IOException) { }
        try { recorder.measure(PerformanceStage.PROFILE_COMMIT) { now += 2_000_000;throw CancellationException() } } catch (_: CancellationException) { }
        val value = recorder.snapshot().single()
        assertEquals(1,value.complete);assertEquals(1,value.failed);assertEquals(1,value.cancelled)
        assertEquals(8L,value.p50Ms);assertEquals(2L,value.lastMs)
    }
    @Test fun failedOnlyWindowsHaveNoSuccessfulPercentilesAndClampingIsExplicit() {
        val recorder = PerformanceRecorder()
        recorder.recordMillis(PerformanceStage.REFRESH,Long.MAX_VALUE,PerformanceOutcome.FAILED)
        val value = recorder.snapshot().single()
        assertNull(value.p50Ms);assertNull(value.maxMs);assertEquals(600_000L,value.lastMs);assertEquals(1,value.clamped)
    }
    @Test fun stageAndScreenCombinationsAreBoundedRatherThanArbitraryRouteStrings() {
        val recorder = PerformanceRecorder()
        assertThrows(IllegalArgumentException::class.java) { recorder.recordMillis(PerformanceStage.NAV_COMPOSE,1) }
        assertThrows(IllegalArgumentException::class.java) { recorder.recordMillis(PerformanceStage.REFRESH,1,screen = PerformanceScreen.HOME) }
        for (screen in PerformanceScreen.entries) repeat(100) { recorder.recordMillis(PerformanceStage.NAV_COMPOSE,1,screen = screen) }
        assertEquals(PerformanceScreen.entries.size,recorder.snapshot().size)
        assertTrue(recorder.snapshot().all { it.samples == 32 })
    }
    @Test fun snapshotsAreIndependentAndConcurrentWritersCannotExceedTheWindow() {
        val recorder = PerformanceRecorder();val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (1..8).map { pool.submit { repeat(5000) { recorder.recordMillis(PerformanceStage.HOME_FILTER,it.toLong()) } } }
            jobs.forEach { it.get(10,TimeUnit.SECONDS) }
            val old = recorder.snapshot();recorder.recordMillis(PerformanceStage.HOME_FILTER,9)
            assertEquals(32,old.single().samples);assertEquals(32,recorder.snapshot().single().samples)
            assertTrue(old.single().valid());assertTrue(recorder.snapshot().single().valid())
        } finally { pool.shutdownNow() }
    }
}