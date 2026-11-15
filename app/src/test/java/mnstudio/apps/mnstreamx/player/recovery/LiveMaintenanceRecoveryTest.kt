package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import android.media.AudioManager
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class LiveMaintenanceRecoveryTest {
    @Test fun theSameChannelRetriesThroughSixteenMinutesOfProviderMaintenance() {
        MockWebServer().use { server ->
            val failures = 8
            val cooldownMs = 120_000L
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
                    if (request.path != "/live.m3u8") return MockResponse().setResponseCode(404)
                    val number = playlistRequests.incrementAndGet()
                    if (number > failures) return MockResponse().setBody(playlist)
                    val status = if (number % 2 == 1) 458 else 503
                    return MockResponse().setResponseCode(status).setHeader("Retry-After", "120")
                }
            }

            val looper = shadowOf(Looper.getMainLooper())
            withLiveSession(server.url("/live.m3u8").toString()) { session, errors, phases ->
                val startedAt = SystemClock.elapsedRealtime()
                session.start()
                repeat(failures) { index ->
                    val attempt = index + 1
                    awaitPlayer("source error $attempt") { errors.size >= attempt }
                    val scheduled = LiveRecoveryState(LiveRecoveryPhase.SCHEDULED, attempt)
                    assertEquals(scheduled, phases.last())
                    assertEquals(attempt, playlistRequests.get())

                    looper.idleFor(cooldownMs - 1, TimeUnit.MILLISECONDS)
                    assertEquals(scheduled, phases.last())
                    assertEquals(attempt, playlistRequests.get())
                    looper.idleFor(1, TimeUnit.MILLISECONDS)
                }

                awaitPlayer("media from the restored channel") {
                    generateSequence { server.takeRequest(0, TimeUnit.MILLISECONDS) }.any { it.path == "/segment.ts" }
                }
                assertTrue(SystemClock.elapsedRealtime() - startedAt >= failures * cooldownMs)
                assertEquals(List(failures) { "ERROR_CODE_IO_BAD_HTTP_STATUS" }, errors)
                assertFalse(phases.any { it.phase == LiveRecoveryPhase.FAILED })
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.CONNECTING, failures), phases.last())
                assertEquals("live", session.player?.currentMediaItem?.mediaId?.substringBefore(':'))
            }
        }
    }

    @Test fun theThirdReconnectCanReturnAnUnreadableResponseWithoutEndingRecovery() {
        MockWebServer().use { server ->
            // The initial load and the first two reconnects receive the provider's error status.
            repeat(3) { server.enqueue(MockResponse().setResponseCode(458)) }
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Maintenance</html>"))
            val sampleBytes = 16_000
            val audio = Buffer().writeUtf8("RIFF").writeIntLe(36 + sampleBytes).writeUtf8("WAVE")
                .writeUtf8("fmt ").writeIntLe(16).writeShortLe(1).writeShortLe(1)
                .writeIntLe(8_000).writeIntLe(16_000).writeShortLe(2).writeShortLe(16)
                .writeUtf8("data").writeIntLe(sampleBytes).write(ByteArray(sampleBytes))
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/wav").setBody(audio))

            val looper = shadowOf(Looper.getMainLooper())
            withLiveSession(server.url("/live.ts").toString()) { session, errors, phases ->
                session.start()
                listOf(1_000L, 3_000L, 7_000L).forEachIndexed { index, delayMs ->
                    awaitPlayer("HTTP error ${index + 1}") { errors.size > index }
                    assertEquals(LiveRecoveryState(LiveRecoveryPhase.SCHEDULED, index + 1), phases.last())
                    looper.idleFor(delayMs, TimeUnit.MILLISECONDS)
                }

                awaitPlayer("unreadable maintenance response") { errors.size == 4 }
                assertEquals("ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED", errors.last())
                assertEquals(LiveRecoveryState(LiveRecoveryPhase.SCHEDULED, 4), phases.last())
                assertEquals(4, server.requestCount)
                looper.idleFor(15_000, TimeUnit.MILLISECONDS)

                awaitPlayer("audio tracks from the restored channel") {
                    session.player?.currentTracks?.isTypeSupported(C.TRACK_TYPE_AUDIO) == true
                }
                assertEquals(5, server.requestCount)
                assertFalse(phases.any { it.phase == LiveRecoveryPhase.FAILED })
                assertTrue(List(5) { server.takeRequest(0, TimeUnit.MILLISECONDS)?.path }.all { it == "/live.ts" })
            }
        }
    }

    private fun withLiveSession(
        url: String,
        run: (PlayerPlaybackSession, List<String>, List<LiveRecoveryState>) -> Unit
    ) {
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application.getSystemService(AudioManager::class.java))
            .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        val errors = mutableListOf<String>()
        val phases = mutableListOf<LiveRecoveryState>()
        val session = PlayerPlaybackSession(
            controller = PlayerController(application),
            initialChannel = Channel("live", "Live channel", streamUrl = url),
            scope = scope,
            network = MutableStateFlow(PlaybackConnection.Online(1, validated = true)),
            onError = { _, error -> if (error != null) errors.add(error) },
            onBuffering = { _, _ -> },
            isSelected = { true },
            onRecovery = { _, state -> phases.add(state) }
        )
        try {
            run(session, errors, phases)
        } finally {
            try {
                session.stop()
            } finally {
                scope.cancel()
            }
        }
    }

    private fun awaitPlayer(description: String, ready: () -> Boolean) {
        val looper = shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            looper.idle()
            if (ready()) return
            Thread.sleep(10)
            ShadowSystemClock.advanceBy(Duration.ofMillis(10))
        }
        throw AssertionError("Timed out waiting for $description")
    }
}