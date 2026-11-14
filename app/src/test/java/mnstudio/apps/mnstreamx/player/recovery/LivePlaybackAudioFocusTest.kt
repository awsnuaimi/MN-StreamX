package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.player.controller.PlayerController
import mnstudio.apps.mnstreamx.ui.component.player.PlayerPlaybackSession
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@androidx.annotation.OptIn(UnstableApi::class)
class LivePlaybackAudioFocusTest {
    @Test fun transientAudioFocusLossDoesNotRestartTheMediaAndItsReturnRearmsTheWatchdog() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val application = RuntimeEnvironment.getApplication()
            val audio = shadowOf(application.getSystemService(AudioManager::class.java))
            audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val looper = shadowOf(Looper.getMainLooper())
            val phases = mutableListOf<LiveRecoveryPhase>()
            val errors = mutableListOf<String>()
            val buffering = mutableListOf<Boolean>()
            val session = newSession(server, errors, { phases.add(it) }, { buffering.add(it) })
            try {
                session.start()
                runCurrent()
                val player = checkNotNull(session.player)
                val original = player.currentMediaItem?.mediaId
                val focus = audio.lastAudioFocusRequest.listener

                focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                looper.idle()
                assertTrue(player.playWhenReady)
                assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS, player.playbackSuppressionReason)
                assertFalse(buffering.last())
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals(original, player.currentMediaItem?.mediaId)
                assertTrue(errors.isEmpty())
                assertTrue(phases.isEmpty())

                focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                looper.idle()
                assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, player.playbackSuppressionReason)
                assertTrue(buffering.last())
                advanceTimeBy(LiveRecoveryController.STALL_TIMEOUT_MS + 1)
                runCurrent()
                assertEquals(1, errors.size)
                assertEquals(LiveRecoveryPhase.SCHEDULED, phases.last())
            } finally {
                session.stop()
            }
        }
    }

    @Test fun aSourceFailureDuringTransientFocusLossRetriesAfterFocusReturns() = runTest {
        MockWebServer().use { server ->
            val releaseResponse = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (server.requestCount > 1) return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                    if (!releaseResponse.await(5, TimeUnit.SECONDS)) return MockResponse().setResponseCode(500)
                    return MockResponse().setResponseCode(503).setHeader("Retry-After", "2")
                }
            }
            val application = RuntimeEnvironment.getApplication()
            val audio = shadowOf(application.getSystemService(AudioManager::class.java))
            audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val looper = shadowOf(Looper.getMainLooper())
            val phases = mutableListOf<LiveRecoveryPhase>()
            val errors = mutableListOf<String>()
            val session = newSession(server, errors, { phases.add(it) })
            try {
                session.start()
                runCurrent()
                assertEquals("/live.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                val player = checkNotNull(session.player)
                val original = player.currentMediaItem?.mediaId
                val focus = audio.lastAudioFocusRequest.listener
                focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                looper.idle()
                assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS, player.playbackSuppressionReason)

                releaseResponse.countDown()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (errors.isEmpty() && System.nanoTime() < deadline) {
                    looper.idleFor(10, TimeUnit.MILLISECONDS)
                    Thread.sleep(10)
                }
                assertEquals(listOf("ERROR_CODE_IO_BAD_HTTP_STATUS"), errors)
                assertNull("Keep the registered audio-focus request until Android grants focus again",
                    audio.lastAbandonedAudioFocusRequest)
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals(1, server.requestCount)
                assertTrue(phases.isEmpty())

                focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                looper.idle()
                assertEquals(LiveRecoveryPhase.SCHEDULED, phases.last())
                advanceTimeBy(3_000)
                runCurrent()
                assertNotEquals(original, player.currentMediaItem?.mediaId)
                assertEquals("/live.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
            } finally {
                releaseResponse.countDown()
                session.stop()
            }
        }
    }

    private fun TestScope.newSession(
        server: MockWebServer,
        errors: MutableList<String>,
        onRecovery: (LiveRecoveryPhase) -> Unit,
        onBuffering: (Boolean) -> Unit = {}
    ) = PlayerPlaybackSession(
        controller = PlayerController(RuntimeEnvironment.getApplication()),
        initialChannel = Channel("test", "Test channel", streamUrl = server.url("/live.ts").toString()),
        scope = this,
        network = emptyFlow(),
        onError = { _, error -> if (error != null) errors.add(error) },
        onBuffering = { _, loading -> onBuffering(loading) },
        isSelected = { true },
        onRecovery = { _, state -> onRecovery(state.phase) }
    )
}