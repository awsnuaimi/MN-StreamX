package com.yourapp.iptv.domain.performance

import org.junit.Assert.*
import org.junit.Test

class PerformanceSpansTest {
    @Test fun oldDrawAndDisposalCallbacksCannotFinishANewerActivitySpan() {
        var now = 0L;val recorder = PerformanceRecorder { now };val spans = PerformanceSpans(recorder)
        val old = spans.start(PerformanceStage.ACTIVITY_HOME);now = 2_000_000
        val current = spans.start(PerformanceStage.ACTIVITY_HOME)
        assertFalse(spans.finish(old));assertEquals(current,spans.currentTicket())
        now = 7_000_000;assertTrue(spans.finish(current));assertFalse(spans.finish(current))
        val row = recorder.snapshot().single();assertEquals(1,row.cancelled);assertEquals(1,row.complete);assertEquals(5L,row.p50Ms)
    }
    @Test fun leavingANavigationBeforeFramesRecordsCancellationForItsOwnScreen() {
        var now = 0L;val recorder = PerformanceRecorder { now };val spans = PerformanceSpans(recorder)
        val first = spans.start(PerformanceStage.NAV_COMPOSE,PerformanceScreen.HOME);now = 10_000_000
        val second = spans.start(PerformanceStage.NAV_COMPOSE,PerformanceScreen.SETTINGS)
        assertFalse(spans.finish(first));now = 13_000_000;spans.finish(second)
        val rows = recorder.snapshot().associateBy { it.screen }
        assertEquals(1,rows.getValue(PerformanceScreen.HOME).cancelled)
        assertEquals(3L,rows.getValue(PerformanceScreen.SETTINGS).p50Ms)
    }
    @Test fun unavailableTicketsDoNotCreateSyntheticMeasurements() {
        val recorder = PerformanceRecorder();val spans = PerformanceSpans(recorder)
        assertNull(spans.currentTicket());assertFalse(spans.finish(null));assertFalse(spans.finish(12));assertTrue(recorder.snapshot().isEmpty())
    }
}