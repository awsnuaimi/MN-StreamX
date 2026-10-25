package com.yourapp.iptv.ui.component.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.yourapp.iptv.domain.model.Channel

class FrameCapturePolicyTest {
    @Test fun retryWindowIsFiniteAndStartsAfterFirstFrame() {
        assertEquals(6, FrameCapturePolicy.MAX_ATTEMPTS)
        assertTrue(FrameCapturePolicy.delayBeforeMs(0) < 900L)
        assertTrue((0 until FrameCapturePolicy.MAX_ATTEMPTS).sumOf(FrameCapturePolicy::delayBeforeMs) < 9000L)
    }

    @Test fun transientFailuresMayRetryOnlyInsideWindow() {
        for (outcome in listOf(FrameCaptureResult.SURFACE_NOT_READY,
                FrameCaptureResult.EMPTY_IMAGE, FrameCaptureResult.COPY_FAILED)) {
            assertTrue(FrameCapturePolicy.continueAfter(outcome, 1))
            assertTrue(FrameCapturePolicy.continueAfter(outcome, 3))
            assertTrue(FrameCapturePolicy.continueAfter(outcome, 4))
            assertFalse(FrameCapturePolicy.continueAfter(outcome, 6))
        }
    }

    @Test fun successAndProtectedSurfacesNeverRetry() {
        for (outcome in listOf(FrameCaptureResult.SAVED, FrameCaptureResult.ALREADY_CACHED,
                FrameCaptureResult.PROTECTED_OR_INVALID, FrameCaptureResult.SAVE_FAILED,
                FrameCaptureResult.STALE_SESSION)) {
            assertFalse(FrameCapturePolicy.continueAfter(outcome, 1))
        }
    }

    @Test fun unknownActivityDoesNotPretendToBeASecureWindow() {
        assertFalse(FrameCapturePolicy.isProtected(null, 8192))
        assertFalse(FrameCapturePolicy.isProtected(0, 8192))
        assertFalse(FrameCapturePolicy.isProtected(128, 8192))
        assertTrue(FrameCapturePolicy.isProtected(8192 or 128, 8192))
    }

    @Test fun resumedPlaybackAndChangedStreamsCannotReuseAnOldFirstFrameEvent() {
        val channel = Channel(id = "one", name = "Test", streamUrl = "https://example.org/stream")
        val old = RenderedChannelFrame(channel, "one:1")
        val resumed = RenderedChannelFrame(channel, "one:3")
        assertTrue(old.matches(channel, "one:1"))
        assertFalse(old.matches(channel, "one:3"))
        assertTrue(resumed.matches(channel, "one:3"))
        assertFalse(old == resumed)
        assertFalse(resumed.matches(channel.copy(streamUrl = "https://other.example/stream"), "one:3"))
        assertFalse(resumed.matches(channel, null))
    }
}