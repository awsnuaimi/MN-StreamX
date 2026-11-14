package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
class LiveOfflineRecoveryTest {
    @Test fun theSameChannelReconnectsEvenWhenTheNetworkReturnIsNeverReported() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "2"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

            val application = RuntimeEnvironment.getApplication()
            shadowOf(application.getSystemService(AudioManager::class.java))
                .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val network = MutableStateFlow<PlaybackConnection>(PlaybackConnection.Online(1))
            val errors = mutableListOf<String>()
            val recovery = mutableListOf<LiveRecoveryState>()
            val session = PlayerPlaybackSession(
                controller = PlayerController(application),
                initialChannel = Channel("test", "Test channel", streamUrl = server.url("/live.ts").toString()),
                scope = this,
                network = network,
                onError = { _, error -> if (error != null) errors.add(error) },
                onBuffering = { _, _ -> },
                isSelected = { true },
                onRecovery = { _, state -> recovery.add(state) }
            )
            try {
                session.start()
                runCurrent()
                assertEquals("/live.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                val player = checkNotNull(session.player)
                val original = player.currentMediaItem?.mediaId

                network.value = PlaybackConnection.Offline
                runCurrent()
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.WAITING_NETWORK, 0), recovery.last())
                advanceTimeBy(14_999)
                runCurrent()
                assertNull(server.takeRequest(0, TimeUnit.MILLISECONDS))
                advanceTimeBy(1)
                runCurrent()
                assertEquals("/live.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                val secondMediaId = player.currentMediaItem?.mediaId
                assertNotEquals(original, secondMediaId)
                awaitPlayer { errors.isNotEmpty() }

                assertEquals(listOf("ERROR_CODE_IO_BAD_HTTP_STATUS"), errors)
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.WAITING_NETWORK, 1), recovery.last())
                advanceTimeBy(14_999)
                runCurrent()
                assertNull(server.takeRequest(0, TimeUnit.MILLISECONDS))
                advanceTimeBy(1)
                runCurrent()
                assertEquals("/live.ts", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                assertNotEquals(secondMediaId, player.currentMediaItem?.mediaId)
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.CONNECTING, 2), recovery.last())
                assertEquals(PlaybackConnection.Offline, network.value)
            } finally {
                session.stop()
            }
        }
    }

    private fun awaitPlayer(condition: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            assertTrue("The player did not report the source error", System.nanoTime() < deadline)
            looper.idleFor(10, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }
}