package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import android.media.AudioManager
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.util.UnstableApi
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
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
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(UnstableApi::class)
class LivePlaylistRecoveryTest {
    @Test
    fun repeatedMalformedPlaylistsAreRetriedUntilTheServerPublishesMediaAgain() {
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
            val looper = shadowOf(Looper.getMainLooper())
            // Use the same Android/Robolectric clock for the recovery coroutine and deadlines.
            // runTest's virtual scheduler is independent of SystemClock.elapsedRealtime().
            val scope = CoroutineScope(Dispatchers.Main.immediate)
            val errors = mutableListOf<String>()
            val states = mutableListOf<LiveRecoveryState>()
            val session = PlayerPlaybackSession(
                controller = PlayerController(application),
                initialChannel = Channel(
                    "test", "Test channel", streamUrl = server.url("/live.m3u8").toString()
                ),
                scope = scope,
                network = emptyFlow(),
                onError = { _, error -> if (error != null) errors.add(error) },
                onBuffering = { _, _ -> },
                isSelected = { true },
                onRecovery = { _, state -> states.add(state) }
            )
            try {
                session.start()
                val player = checkNotNull(session.player)
                val originalMediaId = player.currentMediaItem?.mediaId

                listOf(1_000L, 3_000L, 7_000L).forEachIndexed { index, backoffMs ->
                    val request = awaitPlayer("playlist request ${index + 1}") {
                        server.takeRequest(0, TimeUnit.MILLISECONDS)
                    }
                    assertEquals("/live.m3u8", request.path)
                    awaitPlayer("malformed playlist error ${index + 1}") {
                        Unit.takeIf {
                            errors.size > index && states.any { state ->
                                state.phase == LiveRecoveryPhase.SCHEDULED &&
                                    state.attempt == index + 1
                            }
                        }
                    }
                    assertEquals("ERROR_CODE_PARSING_MANIFEST_MALFORMED", errors[index])
                    val scheduled = states.last { state ->
                        state.phase == LiveRecoveryPhase.SCHEDULED && state.attempt == index + 1
                    }
                    val deadline = checkNotNull(scheduled.retryAtElapsedRealtimeMs)
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    assertTrue("Retry deadline already passed for attempt ${index + 1}", remaining > 0L)
                    assertTrue("Backoff unexpectedly exceeded the policy", remaining <= backoffMs)
                    // Check that the retry does not start before its own SystemClock deadline.
                    looper.idleFor(remaining - 1L, TimeUnit.MILLISECONDS)
                    assertEquals(index + 1, playlistRequests.get())
                    looper.idleFor(1L, TimeUnit.MILLISECONDS)
                }

                val restored = awaitPlayer("restored playlist request") {
                    server.takeRequest(0, TimeUnit.MILLISECONDS)
                }
                assertEquals("/live.m3u8", restored.path)
                awaitPlayer("a media segment from the restored playlist") {
                    var segmentSeen = false
                    var next = server.takeRequest(0, TimeUnit.MILLISECONDS)
                    while (next != null) {
                        if (next.path == "/segment.ts") segmentSeen = true
                        next = server.takeRequest(0, TimeUnit.MILLISECONDS)
                    }
                    Unit.takeIf { segmentSeen }
                }
                assertNotEquals(originalMediaId, player.currentMediaItem?.mediaId)
                assertEquals(3, errors.size)
                assertTrue(playlistRequests.get() >= 4)
                assertTrue(states.none { it.phase == LiveRecoveryPhase.FAILED })
            } finally {
                try {
                    session.stop()
                } finally {
                    scope.cancel()
                }
            }
        }
    }

    private fun <T : Any> awaitPlayer(message: String, read: () -> T?): T {
        val looper = shadowOf(Looper.getMainLooper())
        val timeoutAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(7)
        while (System.nanoTime() < timeoutAt) {
            looper.idle()
            read()?.let { return it }
            // ExoPlayer's worker thread uses real scheduling; let it progress without
            // advancing Android's recovery deadline by a large artificial amount.
            Thread.sleep(10L)
            ShadowSystemClock.advanceBy(Duration.ofMillis(1L))
        }
        throw AssertionError("Timed out waiting for $message")
    }
}