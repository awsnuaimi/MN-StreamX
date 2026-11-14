package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.player.controller.PlayerController
import mnstudio.apps.mnstreamx.ui.component.player.PlayerPlaybackSession
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class LivePlaylistRecoveryTest {
    @Test fun repeatedMalformedPlaylistsAreRetriedUntilTheServerPublishesMediaAgain() = runTest {
        MockWebServer().use { server ->
            val playlistRequests = AtomicInteger()
            val playlist = """
                #EXTM3U
                #EXT-X-TARGETDURATION:6
                #EXT-X-MEDIA-SEQUENCE:10
                #EXTINF:6.0,
                segment.ts
            """.trimIndent()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/segment.ts") {
                        return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                    }
                    val body = if (playlistRequests.incrementAndGet() <= 3) {
                        "<html>Source restarting</html>"
                    } else {
                        playlist
                    }
                    return MockResponse().setBody(body)
                }
            }

            val application = RuntimeEnvironment.getApplication()
            shadowOf(application.getSystemService(AudioManager::class.java))
                .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val errors = mutableListOf<String>()
            val recovery = mutableListOf<LiveRecoveryState>()
            val session = PlayerPlaybackSession(
                controller = PlayerController(application),
                initialChannel = Channel("test", "Test channel", streamUrl = server.url("/live.m3u8").toString()),
                scope = this,
                network = emptyFlow(),
                onError = { _, error -> if (error != null) errors.add(error) },
                onBuffering = { _, _ -> },
                isSelected = { true },
                onRecovery = { _, state -> recovery.add(state) }
            )
            try {
                session.start()
                runCurrent()
                val player = checkNotNull(session.player)
                val firstMediaId = player.currentMediaItem?.mediaId
                listOf(1_000L, 3_000L, 7_000L).forEachIndexed { index, retryDelay ->
                    assertEquals("/live.m3u8", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                    awaitPlayer("The player did not report the source error") { errors.size > index }
                    assertEquals("ERROR_CODE_PARSING_MANIFEST_MALFORMED", errors[index])
                    assertEquals(LiveRecoveryState(LiveRecoveryPhase.SCHEDULED, index + 1), recovery.last())
                    advanceTimeBy(retryDelay - 10)
                    runCurrent()
                    assertEquals(index + 1, playlistRequests.get())
                    advanceTimeBy(20)
                    runCurrent()
                }

                assertEquals("/live.m3u8", server.takeRequest(5, TimeUnit.SECONDS)?.path)
                awaitPlayer("The player did not request media from the restored playlist") {
                    generateSequence { server.takeRequest(0, TimeUnit.MILLISECONDS) }
                        .any { it.path == "/segment.ts" }
                }
                assertNotEquals(firstMediaId, player.currentMediaItem?.mediaId)
                assertEquals(3, errors.size)
                assertTrue(recovery.none { it.phase == LiveRecoveryPhase.FAILED })
            } finally {
                session.stop()
            }
        }
    }

    private fun awaitPlayer(message: String, condition: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            assertTrue(message, System.nanoTime() < deadline)
            looper.idleFor(10, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }
}