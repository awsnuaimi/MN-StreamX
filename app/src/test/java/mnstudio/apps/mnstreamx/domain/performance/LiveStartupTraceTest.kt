package mnstudio.apps.mnstreamx.domain.performance

import org.junit.Assert.*
import org.junit.Test

class LiveStartupTraceTest {
    @Test fun milestonesMeasureSeparatePhasesAndAreRecordedOnce() {
        var now = 0L
        val recorder = PerformanceRecorder { now }
        val trace = LiveStartupTrace(recorder)
        now = 100_000_000
        trace.reached(PerformanceStage.LIVE_HTTP_OPEN)
        now = 150_000_000
        trace.reached(PerformanceStage.LIVE_FIRST_BYTE)
        now = 300_000_000
        trace.reached(PerformanceStage.LIVE_TRACKS_READY)
        now = 500_000_000
        trace.reached(PerformanceStage.LIVE_PLAYER_READY)
        now = 620_000_000
        trace.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        trace.reached(PerformanceStage.LIVE_FIRST_BYTE)
        trace.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        trace.close()
        val rows = recorder.snapshot().associateBy { it.stage }
        assertEquals(100L, rows.getValue(PerformanceStage.LIVE_HTTP_OPEN).lastMs)
        assertEquals(150L, rows.getValue(PerformanceStage.LIVE_FIRST_BYTE).lastMs)
        assertEquals(500L, rows.getValue(PerformanceStage.LIVE_PLAYER_READY).lastMs)
        assertEquals(620L, rows.getValue(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME).lastMs)
        assertTrue(rows.values.all { it.samples == 1 && it.complete == 1 })
    }

    @Test fun cancelledAndFailedAttemptsCannotTurnLateCallbacksIntoSuccess() {
        for (outcome in listOf(PerformanceOutcome.CANCELLED, PerformanceOutcome.FAILED)) {
            val recorder = PerformanceRecorder { 0 }
            val trace = LiveStartupTrace(recorder)
            trace.reached(PerformanceStage.LIVE_HTTP_OPEN)
            trace.close(outcome)
            trace.close(outcome)
            trace.reached(PerformanceStage.LIVE_FIRST_BYTE)
            trace.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
            val rows = recorder.snapshot().associateBy { it.stage }
            assertEquals(1, rows.getValue(PerformanceStage.LIVE_HTTP_OPEN).complete)
            assertEquals(0, rows.getValue(PerformanceStage.LIVE_FIRST_BYTE).complete)
            assertEquals(0, rows.getValue(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME).complete)
            assertTrue(rows.values.all { it.samples == 1 })
        }
    }

    @Test fun httpOpenCallbackRunsOnceAndOnlyForTheCurrentAttempt() {
        val recorder = PerformanceRecorder { 0 }
        var callbacks = 0
        val cancelled = LiveStartupTrace(recorder) { callbacks++ }
        cancelled.close()
        cancelled.reached(PerformanceStage.LIVE_HTTP_OPEN)
        assertEquals(0, callbacks)
        val current = LiveStartupTrace(recorder) { callbacks++ }
        current.reached(PerformanceStage.LIVE_TRACKS_READY)
        assertEquals(0, callbacks)
        current.reached(PerformanceStage.LIVE_HTTP_OPEN)
        current.reached(PerformanceStage.LIVE_HTTP_OPEN)
        current.reached(PerformanceStage.LIVE_FIRST_BYTE)
        assertEquals(1, callbacks)
    }

    @Test fun callbacksFromTheOldChannelCannotCompleteTheNewChannel() {
        val recorder = PerformanceRecorder { 0 }
        val old = LiveStartupTrace(recorder)
        old.close()
        val next = LiveStartupTrace(recorder)
        old.reached(PerformanceStage.LIVE_PLAYER_READY)
        old.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        next.reached(PerformanceStage.LIVE_PLAYER_READY)
        next.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        val player = recorder.snapshot().single { it.stage == PerformanceStage.LIVE_PLAYER_READY }
        assertEquals(2, player.samples)
        assertEquals(1, player.cancelled)
        assertEquals(1, player.complete)
        val frame = recorder.snapshot().single { it.stage == PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME }
        assertEquals(2, frame.samples)
        assertEquals(1, frame.cancelled)
        assertEquals(1, frame.complete)
    }

    @Test fun reconnectAttemptsEachGetTheirOwnFirstVideoFrameSample() {
        var now = 0L
        val recorder = PerformanceRecorder { now }
        val first = LiveStartupTrace(recorder)
        now = 200_000_000
        first.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        first.close()
        now = 300_000_000
        val retry = LiveStartupTrace(recorder)
        now = 900_000_000
        retry.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        retry.close()
        val frame = recorder.snapshot().single { it.stage == PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME }
        assertEquals(2, frame.samples)
        assertEquals(2, frame.complete)
        assertEquals(200L, frame.p50Ms?.toLong())
        assertEquals(600L, frame.p95Ms?.toLong())
        assertEquals(600L, frame.lastMs)
    }

    @Test fun failedPlaybackWithoutVideoRecordsFailedAttempt() {
        var now = 0L
        val recorder = PerformanceRecorder { now }
        val trace = LiveStartupTrace(recorder)
        now = 1_234_000_000
        trace.close(PerformanceOutcome.FAILED)
        trace.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        val frame = recorder.snapshot().single { it.stage == PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME }
        assertEquals(1, frame.samples)
        assertEquals(0, frame.complete)
        assertEquals(1, frame.failed)
        assertEquals(1_234L, frame.lastMs)
    }

    @Test fun retiredAttemptCannotCompleteTheNewAttempt() {
        var now = 0L
        val recorder = PerformanceRecorder { now }
        val retired = LiveStartupTrace(recorder)
        now = 50_000_000
        retired.close()
        now = 100_000_000
        val active = LiveStartupTrace(recorder)
        retired.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        now = 350_000_000
        active.reached(PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME)
        val frame = recorder.snapshot().single { it.stage == PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME }
        assertEquals(2, frame.samples)
        assertEquals(1, frame.cancelled)
        assertEquals(1, frame.complete)
        assertEquals(250L, frame.lastMs)
    }
}