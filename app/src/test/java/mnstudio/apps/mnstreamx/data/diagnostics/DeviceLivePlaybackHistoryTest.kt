package mnstudio.apps.mnstreamx.data.diagnostics

import android.app.Application
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackError
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackErrorOrigin
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackErrorStage
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHealthSession
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackHistoryEntry
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruption
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionOutcome
import mnstudio.apps.mnstreamx.domain.performance.LivePlaybackInterruptionTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLivePlaybackHistoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val file get() = File(temporary.root, "live_playback_history.bin")

    @Test fun aNewProcessCanReadTheLastRecoveryPhaseAndTheLaterReturnOfOutput() = runTest {
        val startTime = 1_700_000_000_000L
        var elapsed = 0L
        val writer = open()
        val session = LivePlaybackHealthSession({ elapsed }, history = writer, wallTimeMs = { startTime + elapsed })
        session.buffering()
        session.bufferEnded()
        session.playbackAdvanced()
        runCurrent()
        assertFalse(file.exists())

        elapsed = 2_000
        session.playbackStalled()
        elapsed = 17_000
        val error = LivePlaybackError(LivePlaybackErrorOrigin.WATCHDOG, 2002, null, true)
        session.error(details = error)
        session.recoveryChanged(LiveRecoveryPhase.SCHEDULED)
        elapsed = 18_000
        session.recoveryChanged(LiveRecoveryPhase.CONNECTING)
        session.retry()
        elapsed = 18_400
        session.httpOpened()
        runCurrent()

        val active = open().snapshot().single()
        assertEquals(LivePlaybackInterruptionOutcome.ACTIVE, active.interruption.outcome)
        assertEquals(LivePlaybackInterruptionTrigger.OUTPUT_STALLED, active.trigger)
        assertEquals(LiveRecoveryPhase.CONNECTING, active.phase)
        assertEquals(startTime + 2_000, active.startedAtMs)
        assertEquals(startTime + 18_400, active.observedAtMs)
        assertEquals(16_400L, active.interruption.durationMs)
        assertEquals(400L, active.interruption.connectingMs)
        assertEquals(1, active.interruption.attempts)
        assertTrue(active.httpOpened)
        assertEquals(error, active.interruption.errors.single().error)
        assertEquals(LivePlaybackErrorStage.PLAYBACK, active.interruption.errors.single().stage)

        elapsed = 19_000
        session.playbackAdvanced()
        runCurrent()
        val recovered = open().snapshot().single()
        assertEquals(active.startedAtMs, recovered.startedAtMs)
        assertEquals(startTime + 19_000, recovered.observedAtMs)
        assertEquals(17_000L, recovered.interruption.durationMs)
        assertEquals(LivePlaybackInterruptionOutcome.RECOVERED, recovered.interruption.outcome)
        assertEquals(active.interruption.errors, recovered.interruption.errors)
    }

    @Test fun keepsOnlyTheLatestTwelveAndIgnoresUpdatesForAnEvictedInterruption() = runTest {
        val writer = open()
        val first = Any()
        writer.record(first, entry(0), isStart = true)
        repeat(20) { writer.record(Any(), entry(it.toLong() + 1), isStart = true) }
        writer.record(first, entry(90), isStart = false)
        runCurrent()

        val recent = open().snapshot()
        assertEquals((20L downTo 9L).toList(), recent.map { it.startedAtMs })
        assertTrue(file.length() in 1..LivePlaybackHistoryCodec.MAX_BYTES)
    }

    @Test fun aClearBeforeLoadingDiscardsSavedEntriesAndLateUpdates() = runTest {
        open().record(Any(), entry(1), isStart = true)
        runCurrent()
        assertTrue(file.exists())

        val writer = open()
        val cleared = Any()
        writer.record(cleared, entry(2), isStart = true)
        writer.clear()
        writer.record(cleared, entry(99), isStart = false)
        writer.record(Any(), entry(3), isStart = true)
        runCurrent()
        assertEquals(listOf(entry(3)), open().snapshot())

        writer.clear()
        runCurrent()
        assertFalse(file.exists())
        assertTrue(open().snapshot().isEmpty())
    }

    @Test fun anUnreadableFileDoesNotPreventNewObservationsFromBeingSaved() = runTest {
        file.writeBytes(byteArrayOf(0x4D, 0x4E))
        assertTrue(open().snapshot().isEmpty())
        file.writeBytes(ByteArray(LivePlaybackHistoryCodec.MAX_BYTES.toInt() + 1))
        val writer = open()
        assertTrue(writer.snapshot().isEmpty())

        writer.record(Any(), entry(11), isStart = true)
        runCurrent()
        assertEquals(listOf(entry(11)), open().snapshot())
    }

    private fun TestScope.open() = DeviceLivePlaybackHistory({ file }, backgroundScope)

    private fun entry(time: Long) = LivePlaybackHistoryEntry(
        time, time, LivePlaybackInterruptionTrigger.BUFFERING, LiveRecoveryPhase.NONE, false,
        LivePlaybackInterruption(0, 0, 0, 0, 0, 0, LivePlaybackInterruptionOutcome.ACTIVE)
    )
}