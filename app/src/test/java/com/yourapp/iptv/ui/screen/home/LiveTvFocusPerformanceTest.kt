package com.yourapp.iptv.ui.screen.home

import com.yourapp.iptv.domain.performance.*
import org.junit.Assert.*
import org.junit.Test

class LiveTvFocusPerformanceTest {
    private var now = 0L
    private val recorder = PerformanceRecorder { now }
    private val nav = LiveTvListNavigation(PerformanceSpans(recorder))
    private fun elapsed(ms: Long) { now += ms * 1_000_000 }
    private fun metric() = recorder.snapshot().single()
    @Test fun theCurrentRequestIsCompletedExactlyOnceAfterFocusAcknowledgment() {
        nav.request("private-channel-name"); val ticket = nav.requests.value!!.revision
        elapsed(12); assertTrue(recorder.snapshot().isEmpty())
        nav.finish(ticket); nav.finish(ticket)
        assertEquals(PerformanceStage.LIVE_FOCUS, metric().stage); assertEquals(12L, metric().p50Ms)
        assertEquals(1, metric().samples); assertNull(nav.requests.value)
        assertFalse(metric().toString().contains("private-channel-name"))
    }
    @Test fun rapidInputCancelsThePreviousSpanAndLateCallbacksCannotFinishTheLatest() {
        nav.request("first"); val old = nav.requests.value!!.revision; elapsed(3)
        nav.request("second"); val current = nav.requests.value!!.revision; elapsed(8)
        nav.finish(old); assertTrue(nav.current(current)); nav.finish(current)
        assertEquals(1, metric().cancelled); assertEquals(1, metric().complete); assertEquals(8L, metric().p95Ms)
    }
    @Test fun leavingTheListCancelsPendingWorkWithoutInventingACompletedSample() {
        nav.request("pending"); elapsed(7); nav.cancel(); nav.cancel()
        assertEquals(1, metric().samples); assertEquals(1, metric().cancelled); assertNull(metric().p50Ms)
    }
    @Test fun anOutdatedDisposalCannotCancelTheNewOwnersIntent() {
        val old = Any(); val current = Any()
        nav.attach(old) { false }; nav.attach(current) { false }; nav.request("new")
        nav.detach(old); assertNotNull(nav.requests.value)
        elapsed(5); nav.detach(current)
        assertNull(nav.requests.value); assertEquals(1, metric().cancelled)
    }
    @Test fun aFailedFocusOperationIsExcludedFromSuccessfulLatencyPercentiles() {
        nav.request("failed"); elapsed(40)
        nav.finish(nav.requests.value!!.revision, PerformanceOutcome.FAILED)
        nav.request("ok"); elapsed(2); nav.finish(nav.requests.value!!.revision)
        assertEquals(1, metric().failed); assertEquals(1, metric().complete); assertEquals(2L, metric().p50Ms)
    }
    @Test fun burstsAndCancellationKeepOnlyTheLatest32NumericSamples() {
        repeat(1000) { nav.request("$it"); elapsed(1) }; nav.cancel()
        assertEquals(32, metric().samples); assertEquals(32, metric().cancelled); assertTrue(metric().valid())
    }
}