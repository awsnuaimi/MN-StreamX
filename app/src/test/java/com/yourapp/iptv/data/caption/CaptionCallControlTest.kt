package com.yourapp.iptv.data.caption

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CaptionCallControlTest {
    @Test fun anExpiredRequestDoesNotPoisonTheNextRequest() {
        val control = CaptionCallControl(); var terminations = 0
        val first = control.begin { terminations++ }
        control.expire(first); assertTrue(first.expired)
        control.finish(first)
        val next = control.begin { terminations++ }
        control.checkRunning(next)
        assertFalse(next.expired); assertEquals(1, terminations)
        control.finish(next)
    }
    @Test fun anOldTimerCannotTerminateANewerCall() {
        val control = CaptionCallControl(); var oldCalls = 0; var newCalls = 0
        val old = control.begin { oldCalls++ }; control.finish(old)
        val next = control.begin { newCalls++ }
        control.expire(old); control.finish(old); control.checkRunning(next)
        assertEquals(0, oldCalls); assertEquals(0, newCalls)
        control.expire(next)
        assertEquals(1, newCalls)
        control.finish(next)
    }
    @Test fun aFinishedHandleCanBeClosedBeforeAnAlreadyQueuedTimerRuns() {
        val control = CaptionCallControl(); var closed = false
        val call = control.begin { assertFalse("Closed native handle accessed", closed) }
        control.finish(call); closed = true
        control.expire(call); control.cancel()
        assertFalse(call.expired)
    }
    @Test fun anExplicitStopStillTerminatesTheActiveCallAndPreventsNewWork() {
        val control = CaptionCallControl(); var terminations = 0
        val call = control.begin { terminations++ }
        control.cancel(); control.cancel(); control.expire(call)
        assertEquals(1, terminations); assertFalse(call.expired)
        try { control.checkRunning(call); fail("Stopped request accepted") } catch (_: CancellationException) { }
        control.finish(call)
        try { control.begin { }; fail("Stopped translator reused") } catch (_: CancellationException) { }
    }
    @Test fun requestsCannotOverlapOnOneSerialTranslator() {
        val control = CaptionCallControl()
        val first = control.begin { }
        try { control.begin { }; fail("Concurrent request accepted") } catch (_: IllegalStateException) { }
        control.checkRunning(first); control.finish(first)
    }
}