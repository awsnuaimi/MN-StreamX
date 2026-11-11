package mnstudio.apps.mnstreamx.domain.performance

import org.junit.Assert.*
import org.junit.Test

class LivePlaybackHealthRecorderTest {
    @Test fun recordsFirstFrameAndNonOverlappingBufferingIntervals() {
        var clock = 1000L
        val recorder = LivePlaybackHealthRecorder { clock }
        val session = recorder.begin()
        session.buffering(); session.buffering()
        clock += 1200
        session.bufferEnded()
        session.firstFrame()
        clock += 200
        session.buffering()
        clock += 500
        session.bufferEnded()
        val result = session.snapshot()
        assertEquals(1900L, result.durationMs)
        assertEquals(1200L, result.firstFrameMs)
        assertEquals(2, result.bufferingEvents)
        assertEquals(1700L, result.bufferingMs)
        assertEquals(1200L, result.longestBufferMs)
        assertFalse(result.finished)
    }

    @Test fun includesActiveBufferingAndStopsCountingAfterFinish() {
        var clock = 0L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.buffering()
        clock += 1300
        assertEquals(1300L, session.snapshot().bufferingMs)
        session.finish()
        clock += 5000
        session.buffering(); session.retry(); session.error(); session.firstFrame(); session.finish()
        val value = session.snapshot()
        assertTrue(value.finished)
        assertEquals(1300L, value.durationMs)
        assertEquals(1300L, value.bufferingMs)
        assertNull(value.firstFrameMs)
        assertEquals(0, value.playerErrors)
        assertEquals(0, value.recoveryAttempts)
    }

    @Test fun retainsOnlyEightMostRecentSelectionsAndNeverIdentifiers() {
        var clock = 0L
        val recorder = LivePlaybackHealthRecorder { clock }
        repeat(14) {
            recorder.begin().apply { firstFrame(); finish() }
            clock += 100
        }
        assertEquals(8, recorder.snapshot().size)
        assertTrue(recorder.snapshot().all { it.finished && it.firstFrameMs == 0L })
        recorder.clear()
        assertTrue(recorder.snapshot().isEmpty())
    }

    @Test fun recoveryAndErrorsSurviveRetriesWithinOneSelection() {
        var clock = 100L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.buffering(); clock += 50; session.error()
        session.retry(); session.buffering(); clock += 200; session.bufferEnded()
        session.firstFrame(); session.finish()
        val summary = session.snapshot()
        assertEquals(2, summary.bufferingEvents)
        assertEquals(250L, summary.bufferingMs)
        assertEquals(200L, summary.longestBufferMs)
        assertEquals(1, summary.recoveryAttempts)
        assertEquals(1, summary.playerErrors)
        assertEquals(250L, summary.firstFrameMs)
    }

    @Test fun distinguishesStartupBufferFromRebufferAndRecordsFirstFrameOnlyOnce() {
        var clock = 0L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.buffering()
        clock = 400
        session.bufferEnded()
        clock = 510
        assertEquals(510L, session.firstFrame())
        clock = 700
        assertNull(session.firstFrame()) // A later reconnect must not duplicate FIRST_FRAME.
        session.buffering(); session.buffering()
        clock = 900
        val pending = session.snapshot()
        assertEquals(1, pending.startupBufferingEvents)
        assertEquals(400L, pending.startupBufferingMs)
        assertEquals(1, pending.rebufferingEvents)
        assertEquals(200L, pending.rebufferingMs)
        session.bufferEnded()
        session.finish()
        val done = session.snapshot()
        assertEquals(2, done.bufferingEvents)
        assertEquals(600L, done.bufferingMs)
        assertEquals(200L, done.longestRebufferMs)
        assertEquals(510L, done.firstFrameMs)
    }

    @Test fun aBufferThatBeginsBeforeFirstFrameStaysClassifiedAsStartup() {
        var clock = 0L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.buffering()
        clock = 100
        assertEquals(100L, session.firstFrame())
        clock = 120
        session.bufferEnded()
        assertEquals(120L, session.snapshot().startupBufferingMs)
        assertEquals(0L, session.snapshot().rebufferingMs)
    }

    @Test fun clockMovingBackwardNeverCreatesNegativeMeasurements() {
        var clock = 1000L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.buffering();clock = 900
        session.firstFrame();session.finish()
        val result = session.snapshot()
        assertEquals(0L, result.durationMs)
        assertEquals(0L, result.bufferingMs)
        assertEquals(0L, result.firstFrameMs)
    }
}