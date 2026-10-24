package com.yourapp.iptv.ui.component.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCapturePolicyTest {
    @Test fun retryWindowIsFiniteAndStartsAfterFirstFrame() {
        assertEquals(4, FrameCapturePolicy.MAX_ATTEMPTS)
        assertEquals(1200L, FrameCapturePolicy.delayBeforeMs(0))
        assertEquals(1500L, FrameCapturePolicy.delayBeforeMs(1))
        assertEquals(1500L, FrameCapturePolicy.delayBeforeMs(3))
    }

    @Test fun transientFailuresMayRetryOnlyInsideWindow() {
        for (outcome in listOf(FrameCaptureResult.SURFACE_NOT_READY,
                FrameCaptureResult.EMPTY_IMAGE, FrameCaptureResult.COPY_FAILED)) {
            assertTrue(FrameCapturePolicy.continueAfter(outcome, 1))
            assertTrue(FrameCapturePolicy.continueAfter(outcome, 3))
            assertFalse(FrameCapturePolicy.continueAfter(outcome, 4))
        }
    }

    @Test fun successAndProtectedSurfacesNeverRetry() {
        for (outcome in listOf(FrameCaptureResult.SAVED, FrameCaptureResult.ALREADY_CACHED,
                FrameCaptureResult.PROTECTED_OR_INVALID, FrameCaptureResult.SAVE_FAILED)) {
            assertFalse(FrameCapturePolicy.continueAfter(outcome, 1))
        }
    }
}