package com.yourapp.iptv.player.recovery

import com.yourapp.iptv.domain.model.LiveRecoveryPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRecoveryControllerTest {
    private class Harness(scope: CoroutineScope) {
        var attempts = 0
        var timeouts = 0
        var allowed = true
        val controller: LiveRecoveryController
        init {
            controller = LiveRecoveryController(scope, {}, { attempts++ }, {
                timeouts++; timeout()
            }, canRetry = { allowed })
            controller.reset()
        }
        private fun timeout() = controller.error(true)
    }

    @Test fun retriesHaveIncreasingDelaysAndStopAfterThreeActualAttempts() = runTest {
        val h = Harness(this)
        h.controller.error(true)
        advanceTimeBy(999); runCurrent(); assertEquals(0, h.attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(1, h.attempts)
        h.controller.error(true)
        advanceTimeBy(2_999); runCurrent(); assertEquals(1, h.attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(2, h.attempts)
        h.controller.error(true)
        advanceTimeBy(6_999); runCurrent(); assertEquals(2, h.attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(3, h.attempts)
        h.controller.error(true)
        assertEquals(LiveRecoveryPhase.EXHAUSTED, h.controller.state.phase)
        h.controller.networkChanged(PlaybackConnection.OFFLINE)
        h.controller.networkChanged(PlaybackConnection.ONLINE)
        advanceTimeBy(100_000); runCurrent(); assertEquals(3, h.attempts)
        h.controller.close()
    }

    @Test fun waitingForTheNetworkDoesNotConsumeTheRetryBudget() = runTest {
        val h = Harness(this)
        h.controller.networkChanged(PlaybackConnection.OFFLINE); h.controller.error(true)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(0, h.attempts); assertEquals(0, h.controller.state.attempt)
        assertEquals(LiveRecoveryPhase.WAITING_NETWORK, h.controller.state.phase)
        h.controller.networkChanged(PlaybackConnection.ONLINE)
        advanceTimeBy(1_000); runCurrent(); assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun losingTheNetworkDuringBackoffCancelsTheOldTimer() = runTest {
        val h = Harness(this)
        h.controller.error(true); advanceTimeBy(500); runCurrent()
        h.controller.networkChanged(PlaybackConnection.OFFLINE)
        advanceTimeBy(10_000); runCurrent(); assertEquals(0, h.attempts)
        h.controller.networkChanged(PlaybackConnection.ONLINE)
        advanceTimeBy(1_000); runCurrent(); assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun aNewSelectionCancelsBothTheOldRetryAndItsConnectionTimeout() = runTest {
        val h = Harness(this)
        h.controller.error(true); advanceTimeBy(500); h.controller.reset()
        advanceTimeBy(100_000); runCurrent(); assertEquals(0, h.attempts)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        assertEquals(1, h.attempts)
        h.controller.reset(); advanceTimeBy(100_000); runCurrent()
        assertEquals(0, h.timeouts); assertEquals(1, h.attempts)
        assertEquals(LiveRecoveryPhase.NONE, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun closingTheSessionRejectsLateErrorsAndNetworkEvents() = runTest {
        val h = Harness(this)
        h.controller.error(true); h.controller.close()
        h.controller.networkChanged(PlaybackConnection.ONLINE); h.controller.error(true)
        advanceTimeBy(100_000); runCurrent()
        assertEquals(0, h.attempts); assertEquals(0, h.timeouts)
        assertEquals(LiveRecoveryPhase.NONE, h.controller.state.phase)
    }

    @Test fun aPermanentFailureIsNotRetriedWhenTheNetworkReturns() = runTest {
        val h = Harness(this)
        h.controller.error(false)
        h.controller.networkChanged(PlaybackConnection.OFFLINE)
        h.controller.networkChanged(PlaybackConnection.ONLINE)
        advanceTimeBy(100_000); runCurrent()
        assertEquals(0, h.attempts); assertEquals(LiveRecoveryPhase.FAILED, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun briefReadyEventsCannotCreateAnUnlimitedReconnectLoop() = runTest {
        val h = Harness(this)
        for (delay in listOf(1_000L, 3_000L, 7_000L)) {
            h.controller.error(true); advanceTimeBy(delay); runCurrent(); h.controller.ready()
        }
        h.controller.error(true)
        assertEquals(3, h.attempts); assertEquals(LiveRecoveryPhase.EXHAUSTED, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun playbackThatBreaksBeforeTheStableIntervalKeepsItsUsedBudget() = runTest {
        val h = Harness(this)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.playing(true); advanceTimeBy(14_999); runCurrent()
        h.controller.error(true); advanceTimeBy(1_000); runCurrent(); assertEquals(1, h.attempts)
        advanceTimeBy(2_000); runCurrent(); assertEquals(2, h.attempts)
        assertEquals(2, h.controller.state.attempt)
        h.controller.close()
    }

    @Test fun sustainedActualPlaybackStartsANewBudgetForALaterOutage() = runTest {
        val h = Harness(this)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.playing(true); advanceTimeBy(15_000); runCurrent()
        assertEquals(0, h.controller.state.attempt)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        assertEquals(2, h.attempts); assertEquals(1, h.controller.state.attempt)
        h.controller.close()
    }

    @Test fun aNetworkLossDuringReconnectionCancelsTheConnectionWatchdog() = runTest {
        val h = Harness(this)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.networkChanged(PlaybackConnection.OFFLINE)
        advanceTimeBy(30_000); runCurrent(); assertEquals(0, h.timeouts)
        h.controller.networkChanged(PlaybackConnection.ONLINE)
        advanceTimeBy(3_000); runCurrent(); assertEquals(2, h.attempts)
        h.controller.close()
    }

    @Test fun pausingCancelsAutomaticPlaybackEvenAfterANetworkChange() = runTest {
        val h = Harness(this)
        h.controller.error(true); h.controller.paused()
        h.controller.networkChanged(PlaybackConnection.OFFLINE)
        h.controller.networkChanged(PlaybackConnection.ONLINE)
        advanceTimeBy(100_000); runCurrent(); assertEquals(0, h.attempts)
        h.controller.close()
    }

    @Test fun connectionAttemptsThatNeverBecomeReadyStillHaveAFiniteBudget() = runTest {
        val h = Harness(this)
        h.controller.error(true); advanceTimeBy(100_000); runCurrent()
        assertEquals(3, h.attempts); assertEquals(3, h.timeouts)
        assertEquals(LiveRecoveryPhase.EXHAUSTED, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun anInitialStallIsBoundedAndReadinessCancelsTheStallWatchdog() = runTest {
        val h = Harness(this)
        h.controller.buffering(); advanceTimeBy(29_999); runCurrent(); assertEquals(0, h.timeouts)
        advanceTimeBy(1); runCurrent(); assertEquals(1, h.timeouts)
        h.controller.reset(); h.controller.buffering(); advanceTimeBy(10_000); h.controller.ready()
        advanceTimeBy(30_000); runCurrent(); assertEquals(1, h.timeouts)
        h.controller.close()
    }

    @Test fun selectingAnotherChannelPreservesKnownOfflineState() = runTest {
        val h = Harness(this)
        h.controller.networkChanged(PlaybackConnection.OFFLINE)
        h.controller.reset(); h.controller.error(true)
        advanceTimeBy(100_000); runCurrent(); assertEquals(0, h.attempts)
        assertEquals(LiveRecoveryPhase.WAITING_NETWORK, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun aPausedPlayerIsCheckedBeforeConsumingAnAttemptOrPreparingPlayback() = runTest {
        val h = Harness(this)
        h.controller.error(true); h.allowed = false
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, h.attempts); assertEquals(0, h.controller.state.attempt)
        assertEquals(LiveRecoveryPhase.NONE, h.controller.state.phase)
        h.controller.networkChanged(PlaybackConnection.OFFLINE)
        h.controller.networkChanged(PlaybackConnection.ONLINE)
        advanceTimeBy(100_000); runCurrent(); assertEquals(0, h.attempts)
        h.controller.close()
    }
}