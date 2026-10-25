package com.yourapp.iptv.ui.component.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCapturePolicyTest {
    @Test fun retryWindowIsFiniteAndStartsAfterFirstFrame() {
        assertEquals(6, FrameCapturePolicy.MAX_ATTEMPTS)
        assertEquals(900L, FrameCapturePolicy.delayBeforeMs(0))
        assertEquals(1600L, FrameCapturePolicy.delayBeforeMs(1))
        assertEquals(1600L, FrameCapturePolicy.delayBeforeMs(5))
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
                FrameCaptureResult.PROTECTED_OR_INVALID, FrameCaptureResult.SAVE_FAILED)) {
            assertFalse(FrameCapturePolicy.continueAfter(outcome, 1))
        }
    }
}