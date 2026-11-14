package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.player.controller.PlayerController
import mnstudio.apps.mnstreamx.ui.component.player.PlayerPlaybackSession
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(UnstableApi::class)
class LiveOfflineRecoveryTest {
    @Test fun theSameChannelReconnectsEvenWhenTheNetworkReturnIsNeverReported() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "2"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

            val application = RuntimeEnvironment.getApplication()
            val looper = shadowOf(Looper.getMainLooper())
            shadowOf(application.getSystemService(AudioManager::class.java))
                .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            // The Android dispatcher and SystemClock both follow the Robolectric main looper's time.
            val scope = CoroutineScope(Dispatchers.Main.immediate)
            val network = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Online(1))
            val errors = mutableListOf<String>()
            val recovery = mutableListOf<LiveRecoveryState>()
            val session = PlayerPlaybackSession(
                controller = PlayerController(application),
                initialChannel = Channel("test", "Test channel", streamUrl = server.url("/live.ts").toString()),
                scope = scope,
                network = network,
                onError = { _, error -> if (error != null) errors.add(error) },
                onBuffering = { _, _ -> },
                isSelected = { true },
                onRecovery = { _, state -> recovery.add(state) }
            )
            try {
                session.start()
                assertStreamRequest(server, "initial request")
                val player = checkNotNull(session.player)
                val original = player.currentMediaItem?.mediaId

                network.value = PlaybackConnection.Offline
                looper.idle()
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.WAITING_NETWORK, 0), recovery.last())
                looper.idleFor(14_999, TimeUnit.MILLISECONDS)
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.WAITING_NETWORK, 0), recovery.last())
                assertEquals(1, server.requestCount)
                looper.idleFor(1, TimeUnit.MILLISECONDS)
                assertStreamRequest(server, "first retry")
                val secondMediaId = player.currentMediaItem?.mediaId
                assertNotEquals(original, secondMediaId)
                awaitPlayer("HTTP 503 error") { errors.firstOrNull() }

                assertEquals(listOf("ERROR_CODE_IO_BAD_HTTP_STATUS"), errors)
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.WAITING_NETWORK, 1), recovery.last())
                looper.idleFor(14_999, TimeUnit.MILLISECONDS)
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.WAITING_NETWORK, 1), recovery.last())
                assertEquals(2, server.requestCount)
                looper.idleFor(1, TimeUnit.MILLISECONDS)
                assertStreamRequest(server, "second retry")
                assertNotEquals(secondMediaId, player.currentMediaItem?.mediaId)
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.CONNECTING, 2), recovery.last())
                assertEquals(PlaybackConnection.Offline, network.value)
            } finally {
                try {
                    session.stop()
                } finally {
                    scope.cancel()
                }
            }
        }
    }

    private fun assertStreamRequest(server: MockWebServer, description: String) {
        val request = awaitPlayer(description) { server.takeRequest(0, TimeUnit.MILLISECONDS) }
        assertEquals(description, "/live.ts", request.path)
    }

    private fun <T : Any> awaitPlayer(description: String, read: () -> T?): T {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            looper.idle()
            read()?.let { return it }
            Thread.sleep(10)
            // Let Media3's playback thread run delayed work before draining the main looper again.
            ShadowSystemClock.advanceBy(Duration.ofMillis(10))
        }
        throw AssertionError("Timed out waiting for $description")
    }
}