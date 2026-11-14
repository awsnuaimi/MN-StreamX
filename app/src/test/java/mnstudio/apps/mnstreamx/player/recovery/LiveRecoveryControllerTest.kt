package mnstudio.apps.mnstreamx.player.recovery

import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRecoveryControllerTest {
    private val wifi = PlaybackConnection.Online(1L, validated = true)
    private val ethernet = PlaybackConnection.Online(2L, validated = true)

    private class Harness(scope: CoroutineScope, nowMs: () -> Long) {
        var attempts = 0
        var timeouts = 0
        var allowed = true
        val controller: LiveRecoveryController
        init {
            controller = LiveRecoveryController(scope, {}, { attempts++ }, {
                timeouts++
                timeout()
            }, canRetry = { allowed }, nowMs = nowMs)
            controller.reset()
        }
        private fun timeout() = controller.error(true)
    }

    @Test fun retriesKeepRunningWithCappedBackoffUntilPlaybackAdvances() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true)
        val delays = listOf(1_000L, 3_000L, 7_000L, 15_000L, 30_000L, 30_000L, 30_000L)
        delays.forEachIndexed { index, backoff ->
            assertEquals(LiveRecoveryPhase.SCHEDULED, h.controller.state.phase)
            assertEquals(index + 1, h.controller.state.attempt)
            advanceTimeBy(backoff - 1); runCurrent(); assertEquals(index, h.attempts)
            advanceTimeBy(1); runCurrent(); assertEquals(index + 1, h.attempts)
            assertEquals(LiveRecoveryPhase.CONNECTING, h.controller.state.phase)
            if (index < delays.lastIndex) h.controller.error(true)
        }
        h.controller.playbackAdvanced()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(LiveRecoveryPhase.NONE, h.controller.state.phase)
        assertEquals(delays.size, h.attempts)
        assertEquals(0, h.timeouts)
        h.controller.close()
    }

    @Test fun waitingForTheNetworkDoesNotCountAsAnAttempt() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(PlaybackConnection.Offline); h.controller.error(true)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(0, h.attempts); assertEquals(0, h.controller.state.attempt)
        assertEquals(LiveRecoveryPhase.WAITING_NETWORK, h.controller.state.phase)
        h.controller.networkChanged(wifi)
        advanceTimeBy(1_000); runCurrent(); assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun losingTheNetworkDuringBackoffCancelsTheOldTimer() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true); advanceTimeBy(500); runCurrent()
        h.controller.networkChanged(PlaybackConnection.Offline)
        advanceTimeBy(10_000); runCurrent(); assertEquals(0, h.attempts)
        h.controller.networkChanged(wifi)
        advanceTimeBy(1_000); runCurrent(); assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun aNewSelectionCancelsBothTheOldRetryAndItsConnectionTimeout() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
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
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true); h.controller.close()
        h.controller.networkChanged(wifi); h.controller.error(true)
        advanceTimeBy(100_000); runCurrent()
        assertEquals(0, h.attempts); assertEquals(0, h.timeouts)
        assertEquals(LiveRecoveryPhase.NONE, h.controller.state.phase)
    }

    @Test fun aPermanentFailureIsNotRetriedWhenTheNetworkReturns() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(false)
        h.controller.networkChanged(PlaybackConnection.Offline)
        h.controller.networkChanged(wifi)
        advanceTimeBy(100_000); runCurrent()
        assertEquals(0, h.attempts); assertEquals(LiveRecoveryPhase.FAILED, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun connectionWithoutPlaybackProgressRetainsItsOriginalWatchdog() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true)
        advanceTimeBy(1_000); runCurrent()
        repeat(19) {
            advanceTimeBy(1_000); runCurrent()
            h.controller.awaitingPlayback()
        }
        assertEquals(LiveRecoveryPhase.CONNECTING, h.controller.state.phase)
        assertEquals(0, h.timeouts)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, h.timeouts)
        assertEquals(LiveRecoveryPhase.SCHEDULED, h.controller.state.phase)
        assertEquals(2, h.controller.state.attempt)
        h.controller.close()
    }

    @Test fun briefPlaybackKeepsTheCurrentBackoff() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.playbackAdvanced(); advanceTimeBy(14_999); runCurrent()
        h.controller.error(true); advanceTimeBy(1_000); runCurrent(); assertEquals(1, h.attempts)
        advanceTimeBy(2_000); runCurrent(); assertEquals(2, h.attempts)
        assertEquals(2, h.controller.state.attempt)
        h.controller.close()
    }

    @Test fun sustainedPlaybackResetsBackoffForTheNextOutage() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.playbackAdvanced(); advanceTimeBy(15_000); runCurrent()
        assertEquals(0, h.controller.state.attempt)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        assertEquals(2, h.attempts); assertEquals(1, h.controller.state.attempt)
        h.controller.close()
    }

    @Test fun aNetworkLossDuringReconnectionCancelsTheConnectionWatchdog() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.networkChanged(PlaybackConnection.Offline)
        advanceTimeBy(30_000); runCurrent(); assertEquals(0, h.timeouts)
        h.controller.networkChanged(wifi)
        advanceTimeBy(999); runCurrent(); assertEquals(1, h.attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(2, h.attempts)
        h.controller.close()
    }

    @Test fun pausingCancelsAutomaticPlaybackEvenAfterANetworkChange() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true)
        h.allowed = false
        h.controller.playbackPermissionChanged()
        h.controller.networkChanged(PlaybackConnection.Offline)
        h.controller.networkChanged(wifi)
        advanceTimeBy(100_000); runCurrent(); assertEquals(0, h.attempts)
        h.controller.close()
    }

    @Test fun aSystemInterruptionStopsTheWatchdogUntilPlaybackCanResume() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.awaitingPlayback()
        advanceTimeBy(5_000)
        h.allowed = false
        h.controller.playbackPermissionChanged()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, h.timeouts)
        assertEquals(0, h.attempts)

        h.allowed = true
        h.controller.playbackPermissionChanged()
        h.controller.awaitingPlayback()
        advanceTimeBy(29_999); runCurrent(); assertEquals(0, h.timeouts)
        advanceTimeBy(1); runCurrent(); assertEquals(1, h.timeouts)
        h.controller.close()
    }

    @Test fun aRealFailureDuringTheInterruptionResumesRecoveryWhenPlaybackIsAllowed() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.allowed = false
        h.controller.error(true)
        advanceTimeBy(60_000); runCurrent(); assertEquals(0, h.attempts)
        h.controller.networkChanged(PlaybackConnection.Offline)
        h.allowed = true
        h.controller.playbackPermissionChanged()
        assertEquals(LiveRecoveryPhase.WAITING_NETWORK, h.controller.state.phase)
        h.controller.networkChanged(wifi)
        advanceTimeBy(1_000); runCurrent(); assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun anInterruptedScheduledRetryKeepsItsServerCooldown() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true, retryAfterMs = 45_000)
        advanceTimeBy(2_000)
        h.allowed = false
        h.controller.playbackPermissionChanged()
        advanceTimeBy(3_000)
        h.allowed = true
        h.controller.playbackPermissionChanged()
        advanceTimeBy(39_999); runCurrent(); assertEquals(0, h.attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun restoredPlaybackPermissionCannotRestartARetiredSelection() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.allowed = false
        h.controller.error(true)
        h.controller.reset()
        h.allowed = true
        h.controller.playbackPermissionChanged()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, h.attempts)
        h.controller.close()
    }

    @Test fun restoredPlaybackPermissionDoesNotRetryPermanentErrors() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.allowed = false
        h.controller.error(false)
        h.allowed = true
        h.controller.playbackPermissionChanged()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, h.attempts)
        assertEquals(LiveRecoveryPhase.FAILED, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun connectionTimeoutsContinuePastTheFormerThreeAttemptLimit() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true)
        advanceTimeBy(156_000); runCurrent()
        assertEquals(5, h.attempts)
        assertEquals(5, h.timeouts)
        assertEquals(LiveRecoveryPhase.SCHEDULED, h.controller.state.phase)
        assertEquals(6, h.controller.state.attempt)
        h.controller.close()
    }

    @Test fun playbackProgressCancelsTheStallWatchdogAndASecondStallStartsANewOne() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.awaitingPlayback(); advanceTimeBy(29_999); runCurrent()
        h.controller.playbackAdvanced(); advanceTimeBy(30_000); runCurrent()
        assertEquals(0, h.timeouts)
        h.controller.awaitingPlayback(); advanceTimeBy(30_000); runCurrent()
        assertEquals(1, h.timeouts)
        assertEquals(LiveRecoveryPhase.SCHEDULED, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun firstStartupWhileOfflineWaitsForTheNetwork() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(PlaybackConnection.Offline)
        assertTrue(h.controller.waitIfOffline())
        advanceTimeBy(90_000); runCurrent()
        assertEquals(LiveRecoveryPhase.WAITING_NETWORK, h.controller.state.phase)
        assertEquals(0, h.timeouts)
        assertEquals(0, h.attempts)
        h.controller.networkChanged(wifi)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun networkFlappingCannotSkipAServerRetryAfter() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true, retryAfterMs = 60_000)
        advanceTimeBy(10_000); runCurrent()
        h.controller.networkChanged(PlaybackConnection.Offline)
        advanceTimeBy(10_000); runCurrent()
        h.controller.networkChanged(wifi)
        advanceTimeBy(39_999); runCurrent()
        assertEquals(0, h.attempts)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun aDefaultNetworkHandoverExpeditesAnAlreadyScheduledRetry() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.error(true)
        h.controller.networkChanged(ethernet)
        advanceTimeBy(999); runCurrent(); assertEquals(1, h.attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(2, h.attempts)
        advanceTimeBy(2_000); runCurrent(); assertEquals(2, h.attempts)
        h.controller.close()
    }

    @Test fun oneNetworkChangeDoesNotAccelerateEverySubsequentFailure() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.error(true)
        h.controller.networkChanged(ethernet)
        advanceTimeBy(1_000); runCurrent(); assertEquals(2, h.attempts)
        h.controller.error(true)
        advanceTimeBy(6_999); runCurrent(); assertEquals(2, h.attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(3, h.attempts)
        h.controller.close()
    }

    @Test fun internetValidationOnTheSameNetworkExpeditesPendingRecovery() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi.copy(validated = false))
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.error(true)
        h.controller.networkChanged(wifi)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, h.attempts)
        h.controller.close()
    }

    @Test fun rapidNetworkUpdatesDoNotPostponeTheEarlierRetry() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi)
        h.controller.error(true)
        advanceTimeBy(500)
        h.controller.networkChanged(ethernet)
        advanceTimeBy(400)
        h.controller.networkChanged(wifi)
        advanceTimeBy(100); runCurrent()
        assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun aHandoverShortensAnInFlightAttemptOnlyWhenProgressDoesNotReturn() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi)
        h.controller.error(true); advanceTimeBy(1_000); runCurrent()
        h.controller.networkChanged(ethernet)
        advanceTimeBy(7_999); runCurrent(); assertEquals(0, h.timeouts)
        advanceTimeBy(1); runCurrent(); assertEquals(1, h.timeouts)
        advanceTimeBy(1_000); runCurrent(); assertEquals(2, h.attempts)
        h.controller.close()
    }

    @Test fun aStallSoonAfterHandoverGetsAShorterWatchdog() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi)
        h.controller.networkChanged(ethernet)
        advanceTimeBy(3_000); runCurrent(); assertEquals(0, h.timeouts)
        h.controller.awaitingPlayback()
        advanceTimeBy(7_999); runCurrent(); assertEquals(0, h.timeouts)
        advanceTimeBy(1); runCurrent(); assertEquals(1, h.timeouts)
        h.controller.close()
    }

    @Test fun anUnrelatedLaterStallUsesTheRegularTimeout() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi)
        h.controller.networkChanged(ethernet)
        advanceTimeBy(15_000)
        h.controller.awaitingPlayback()
        advanceTimeBy(29_999); runCurrent(); assertEquals(0, h.timeouts)
        advanceTimeBy(1); runCurrent(); assertEquals(1, h.timeouts)
        h.controller.close()
    }

    @Test fun progressAfterHandoverCancelsTheShorterStallWatchdog() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi)
        h.controller.awaitingPlayback()
        h.controller.networkChanged(ethernet)
        advanceTimeBy(2_000); runCurrent()
        h.controller.playbackAdvanced()
        h.controller.networkChanged(wifi)
        advanceTimeBy(40_000); runCurrent()
        assertEquals(0, h.timeouts)
        assertEquals(0, h.attempts)
        h.controller.close()
    }

    @Test fun aHandoverCannotSkipAnActiveServerCooldown() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(wifi)
        h.controller.error(true, retryAfterMs = 45_000)
        advanceTimeBy(5_000)
        h.controller.networkChanged(ethernet)
        advanceTimeBy(39_999); runCurrent(); assertEquals(0, h.attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(1, h.attempts)
        h.controller.close()
    }

    @Test fun selectingAnotherChannelPreservesKnownOfflineState() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.networkChanged(PlaybackConnection.Offline)
        h.controller.reset(); h.controller.error(true)
        advanceTimeBy(100_000); runCurrent(); assertEquals(0, h.attempts)
        assertEquals(LiveRecoveryPhase.WAITING_NETWORK, h.controller.state.phase)
        h.controller.close()
    }

    @Test fun aPausedPlayerIsCheckedBeforeConsumingAnAttemptOrPreparingPlayback() = runTest {
        val h = Harness(this) { testScheduler.currentTime }
        h.controller.error(true); h.allowed = false
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, h.attempts); assertEquals(0, h.controller.state.attempt)
        assertEquals(LiveRecoveryPhase.NONE, h.controller.state.phase)
        h.controller.networkChanged(PlaybackConnection.Offline)
        h.controller.networkChanged(wifi)
        advanceTimeBy(100_000); runCurrent(); assertEquals(0, h.attempts)
        h.controller.close()
    }
}