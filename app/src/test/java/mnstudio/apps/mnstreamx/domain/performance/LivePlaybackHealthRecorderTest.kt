package mnstudio.apps.mnstreamx.domain.performance

import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
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

    @Test fun initialLoadingIsExcludedAndRepeatedAttemptsBelongToOneInterruption() {
        var clock = 100L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.buffering()
        session.error()
        session.retry()
        assertEquals(0, session.snapshot().interruptionCount)
        clock = 200
        session.playbackAdvanced()

        clock = 1_000
        session.buffering()
        clock = 1_400
        session.error(retryAfterMs = 5_000L)
        session.recoveryChanged(LiveRecoveryPhase.WAITING_NETWORK)
        clock = 3_400
        session.recoveryChanged(LiveRecoveryPhase.SCHEDULED)
        clock = 6_900
        session.recoveryChanged(LiveRecoveryPhase.CONNECTING)
        session.retry()
        clock = 7_400
        session.httpOpened()
        clock = 7_900
        session.error()
        session.recoveryChanged(LiveRecoveryPhase.SCHEDULED)
        clock = 8_900
        session.recoveryChanged(LiveRecoveryPhase.CONNECTING)
        session.retry()
        clock = 9_300
        session.httpOpened()
        session.bufferEnded()
        assertEquals(LivePlaybackInterruptionOutcome.ACTIVE, session.snapshot().interruptions.single().outcome)
        clock = 9_400
        session.playbackAdvanced()
        session.recoveryChanged(LiveRecoveryPhase.NONE)

        val summary = session.snapshot()
        assertEquals(1, summary.interruptionCount)
        assertEquals(
            LivePlaybackInterruption(8_400L, 2_000L, 3_000L, 1_500L, 1_000L, 2, LivePlaybackInterruptionOutcome.RECOVERED, connectingMs = 900L),
            summary.interruptions.single()
        )
        assertEquals(3, summary.recoveryAttempts)
    }

    @Test fun anInterruptionKeepsItsTriggerAndLatestErrorsWithTheirOwnAttemptAndStage() {
        var clock = 0L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        val watchdog = LivePlaybackError(LivePlaybackErrorOrigin.WATCHDOG, 2002, null, true)
        val network = LivePlaybackError(LivePlaybackErrorOrigin.NETWORK_TIMEOUT, 2002, null, true)
        val provider = LivePlaybackError(LivePlaybackErrorOrigin.PLAYER, 2004, 458, true)
        val terminal = LivePlaybackError(LivePlaybackErrorOrigin.PLAYER, 2004, 403, false)

        session.error(details = network)
        assertEquals(0, session.snapshot().interruptionCount)
        session.playbackAdvanced()
        clock = 1_000
        session.buffering()
        clock = 2_000
        session.error(details = watchdog)
        session.recoveryChanged(LiveRecoveryPhase.SCHEDULED)
        clock = 3_000
        session.recoveryChanged(LiveRecoveryPhase.CONNECTING)
        session.retry()
        clock = 4_000
        session.error(details = provider)
        session.recoveryChanged(LiveRecoveryPhase.SCHEDULED)
        clock = 5_000
        session.recoveryChanged(LiveRecoveryPhase.CONNECTING)
        session.retry()
        clock = 6_000
        session.httpOpened()
        clock = 7_000
        session.error(details = network)
        session.recoveryChanged(LiveRecoveryPhase.SCHEDULED)
        val active = session.snapshot().interruptions.single()
        assertEquals(LivePlaybackInterruptionOutcome.ACTIVE, active.outcome)
        assertEquals(LivePlaybackErrorStage.CONNECTING, active.errors[1].stage)
        assertEquals(458, active.errors[1].error.httpStatus)

        clock = 8_000
        session.recoveryChanged(LiveRecoveryPhase.CONNECTING)
        session.retry()
        clock = 9_000
        session.error(details = terminal)
        session.recoveryChanged(LiveRecoveryPhase.FAILED)

        val interruption = session.snapshot().interruptions.single()
        assertEquals(LivePlaybackInterruptionOutcome.FAILED, interruption.outcome)
        assertEquals(4, interruption.errorCount)
        assertEquals(3, interruption.attempts)
        assertEquals(
            listOf(
                LivePlaybackInterruptionError(watchdog, 1_000, LivePlaybackErrorStage.BUFFERING, 0),
                LivePlaybackInterruptionError(network, 6_000, LivePlaybackErrorStage.LOADING, 2),
                LivePlaybackInterruptionError(terminal, 8_000, LivePlaybackErrorStage.CONNECTING, 3)
            ),
            interruption.errors
        )
        assertEquals(3, active.errorCount)
        assertEquals(458, active.errors[1].error.httpStatus)
    }

    @Test fun anErrorWithNoAutomaticRetryBelongsOnlyToItsOwnInterruption() {
        var clock = 0L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        val failure = LivePlaybackError(LivePlaybackErrorOrigin.PREPARE, 1000, null, false)
        session.playbackAdvanced()
        session.error(details = failure)
        session.recoveryChanged(LiveRecoveryPhase.FAILED)
        session.playbackAdvanced()
        clock = 500
        session.buffering()
        clock = 800
        session.playbackAdvanced()

        val errors = session.snapshot().interruptions
        assertEquals(0, errors.first().errorCount)
        assertEquals(LivePlaybackInterruptionOutcome.RECOVERED, errors.first().outcome)
        assertEquals(0, errors.last().attempts)
        assertEquals(failure, errors.last().errors.single().error)
    }

    @Test fun playbackProgressClosesAnInPlaceStallWithoutAnyReconnect() {
        var clock = 0L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.playbackAdvanced()
        session.playbackStalled()
        clock = 400
        session.playbackStalled()
        session.buffering()
        session.bufferEnded()
        clock = 900
        assertEquals(LivePlaybackInterruptionOutcome.ACTIVE, session.snapshot().interruptions.single().outcome)
        session.playbackAdvanced()
        assertEquals(
            LivePlaybackInterruption(900L, 0L, 0L, 0L, 900L, 0, LivePlaybackInterruptionOutcome.RECOVERED),
            session.snapshot().interruptions.single()
        )
    }

    @Test fun pausesAndFailuresCloseAnInterruptionWithoutClaimingRecovery() {
        var clock = 0L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.playbackAdvanced()
        session.buffering()
        clock = 200
        session.playbackSuspended()
        clock = 2_000
        session.error(playbackAllowed = false)
        session.buffering()
        session.bufferEnded()
        assertEquals(1, session.snapshot().interruptionCount)
        val stopped = session.snapshot().interruptions.single()
        assertEquals(200L, stopped.durationMs)
        assertEquals(LivePlaybackInterruptionOutcome.STOPPED, stopped.outcome)

        session.playbackAdvanced()
        session.error()
        session.recoveryChanged(LiveRecoveryPhase.FAILED)
        assertEquals(LivePlaybackInterruptionOutcome.FAILED, session.snapshot().interruptions.first().outcome)
    }

    @Test fun onlyRecentDetailsAreRetainedWhileTheTotalKeepsGrowing() {
        var clock = 0L
        val session = LivePlaybackHealthRecorder { clock }.begin()
        session.playbackAdvanced()
        repeat(10) { index ->
            session.playbackStalled()
            clock += index + 1L
            session.playbackAdvanced()
        }
        session.playbackStalled()
        clock += 20
        val summary = session.snapshot()
        assertEquals(11, summary.interruptionCount)
        assertEquals(listOf(20L, 10L, 9L, 8L, 7L, 6L), summary.interruptions.map { it.durationMs })
        assertEquals(LivePlaybackInterruptionOutcome.ACTIVE, summary.interruptions.first().outcome)
        session.finish()
        assertEquals(LivePlaybackInterruptionOutcome.STOPPED, session.snapshot().interruptions.first().outcome)
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