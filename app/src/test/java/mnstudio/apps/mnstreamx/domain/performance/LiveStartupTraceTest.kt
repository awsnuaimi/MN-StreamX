package mnstudio.apps.mnstreamx.domain.performance

import org.junit.Assert.*
import org.junit.Test

class LiveStartupTraceTest {
    @Test fun milestonesMeasureSeparatePhasesAndAreRecordedOnce() {
        var now = 0L;val recorder = PerformanceRecorder { now };val trace = LiveStartupTrace(recorder)
        now = 100_000_000;trace.reached(PerformanceStage.LIVE_HTTP_OPEN)
        now = 150_000_000;trace.reached(PerformanceStage.LIVE_FIRST_BYTE)
        now = 300_000_000;trace.reached(PerformanceStage.LIVE_TRACKS_READY)
        now = 500_000_000;trace.reached(PerformanceStage.LIVE_PLAYER_READY)
        trace.reached(PerformanceStage.LIVE_FIRST_BYTE);trace.close()
        val rows = recorder.snapshot().associateBy { it.stage }
        assertEquals(100L,rows.getValue(PerformanceStage.LIVE_HTTP_OPEN).lastMs)
        assertEquals(150L,rows.getValue(PerformanceStage.LIVE_FIRST_BYTE).lastMs)
        assertEquals(500L,rows.getValue(PerformanceStage.LIVE_PLAYER_READY).lastMs)
        assertTrue(rows.values.all { it.samples == 1 && it.complete == 1 })
    }
    @Test fun cancelledAndFailedAttemptsCannotTurnLateCallbacksIntoSuccess() {
        for (outcome in listOf(PerformanceOutcome.CANCELLED,PerformanceOutcome.FAILED)) {
            val recorder = PerformanceRecorder { 0 };val trace = LiveStartupTrace(recorder)
            trace.reached(PerformanceStage.LIVE_HTTP_OPEN)
            trace.close(outcome);trace.close(outcome)
            trace.reached(PerformanceStage.LIVE_FIRST_BYTE)
            val rows = recorder.snapshot().associateBy { it.stage }
            assertEquals(1,rows.getValue(PerformanceStage.LIVE_HTTP_OPEN).complete)
            assertEquals(0,rows.getValue(PerformanceStage.LIVE_FIRST_BYTE).complete)
            assertTrue(rows.values.all { it.samples == 1 })
        }
    }
    @Test fun callbacksFromTheOldChannelCannotCompleteTheNewChannel() {
        val recorder = PerformanceRecorder { 0 }
        val old = LiveStartupTrace(recorder);old.close()
        val next = LiveStartupTrace(recorder)
        old.reached(PerformanceStage.LIVE_PLAYER_READY)
        next.reached(PerformanceStage.LIVE_PLAYER_READY)
        val value = recorder.snapshot().single { it.stage == PerformanceStage.LIVE_PLAYER_READY }
        assertEquals(2,value.samples);assertEquals(1,value.cancelled);assertEquals(1,value.complete)
    }
}