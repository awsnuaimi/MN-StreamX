package mnstudio.apps.mnstreamx.player.recovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRecoveryLifecycleTest {
    @Test fun returningBeforeTheServerDeadlineKeepsTheRemainingWaitAndAttemptNumber() = runTest {
        val session = Harness(this) { testScheduler.currentTime }
        val recovery = session.recovery
        recovery.error(true)
        advanceTimeBy(1_000)
        runCurrent()
        recovery.error(true, retryAfterMs = 60_000)

        advanceTimeBy(10_000)
        recovery.suspendForBackground()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(1, session.attempts)

        assertTrue(recovery.resume())
        assertEquals(LiveRecoveryState(LiveRecoveryPhase.SCHEDULED, 2), recovery.state)
        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(1, session.attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, session.attempts)
        assertEquals(LiveRecoveryState(LiveRecoveryPhase.CONNECTING, 2), recovery.state)
        recovery.close()
    }

    @Test fun aDeadlineThatExpiredInTheBackgroundAllowsAQuickAttemptAfterReturning() = runTest {
        val session = Harness(this) { testScheduler.currentTime }
        val recovery = session.recovery
        recovery.error(true, retryAfterMs = 10_000)
        recovery.suspendForBackground()
        advanceTimeBy(50_000)
        runCurrent()
        assertEquals(0, session.attempts)

        assertTrue(recovery.resume())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, session.attempts)
        recovery.close()
    }

    @Test fun inFlightAttemptRestartsOnlyOnReturnAndKeepsItsAttemptHistory() = runTest {
        val session = Harness(this) { testScheduler.currentTime }
        val recovery = session.recovery
        recovery.error(true)
        advanceTimeBy(1_000)
        runCurrent()
        recovery.suspendForBackground()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, session.attempts)
        assertEquals(0, session.timeouts)

        assertTrue(recovery.resume())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, session.attempts)
        assertEquals(LiveRecoveryState(LiveRecoveryPhase.CONNECTING, 2), recovery.state)
        recovery.close()
    }

    @Test fun reobservingAnOfflineNetworkKeepsTheFallbackAndServerCooldown() = runTest {
        val session = Harness(this) { testScheduler.currentTime }
        val recovery = session.recovery
        recovery.networkChanged(PlaybackConnection.Offline)
        recovery.error(true, retryAfterMs = 60_000)
        advanceTimeBy(10_000)
        recovery.suspendForBackground()
        advanceTimeBy(10_000)
        assertTrue(recovery.resume())
        recovery.networkChanged(PlaybackConnection.Offline)
        assertEquals(LiveRecoveryPhase.WAITING_NETWORK, recovery.state.phase)
        recovery.networkChanged(PlaybackConnection.Online(1))
        advanceTimeBy(39_999)
        runCurrent()
        assertEquals(0, session.attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, session.attempts)
        recovery.close()
    }

    @Test fun aShortBackgroundVisitKeepsAnOfflineFallbackWhenTheObserverIsSilent() = runTest {
        val session = Harness(this) { testScheduler.currentTime }
        val recovery = session.recovery
        recovery.networkChanged(PlaybackConnection.Offline)
        recovery.error(true)
        advanceTimeBy(5_000)
        recovery.suspendForBackground()
        advanceTimeBy(1_000)
        assertTrue(recovery.resume())

        advanceTimeBy(8_999)
        runCurrent()
        assertEquals(0, session.attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, session.attempts)
        recovery.close()
    }

    @Test fun selectingAnotherChannelDiscardsThePreviousDeadline() = runTest {
        val session = Harness(this) { testScheduler.currentTime }
        val recovery = session.recovery
        recovery.error(true, retryAfterMs = 60_000)
        recovery.suspendForBackground()
        recovery.reset()

        assertFalse(recovery.resume())
        assertEquals(LiveRecoveryState(), recovery.state)
        recovery.error(true)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, session.attempts)
        recovery.close()
    }

    @Test fun returningKeepsPermanentErrorsAndHonorsAnExplicitPause() = runTest {
        val session = Harness(this) { testScheduler.currentTime }
        val recovery = session.recovery
        recovery.error(false)
        recovery.suspendForBackground()
        assertTrue(recovery.resume())
        assertEquals(LiveRecoveryPhase.FAILED, recovery.state.phase)
        recovery.reset()

        recovery.error(true)
        recovery.suspendForBackground()
        session.allowed = false
        assertTrue(recovery.resume())
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(0, session.attempts)
        session.allowed = true
        recovery.playbackPermissionChanged()
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, session.attempts)
        recovery.close()
    }

    private class Harness(scope: CoroutineScope, nowMs: () -> Long) {
        var allowed = true
        var attempts = 0
        var timeouts = 0
        val recovery = LiveRecoveryController(
            scope = scope,
            onState = {},
            retry = { attempts++ },
            onTimeout = { timeouts++ },
            canRetry = { allowed },
            nowMs = nowMs
        ).also { it.reset() }
    }
}