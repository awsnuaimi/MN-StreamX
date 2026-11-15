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
            val scope = CoroutineScope(Dispatchers.Main.immediate)
            val application = RuntimeEnvironment.getApplication()
            shadowOf(application.getSystemService(AudioManager::class.java))
                .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            val network = PlaybackConnection.Online(1, validated = true)
            val errors = mutableListOf<String>()
            val phases = mutableListOf<LiveRecoveryState>()
            val session = PlayerPlaybackSession(
                controller = PlayerController(application),
                initialChannel = Channel("live", "Live channel", streamUrl = server.url("/live.m3u8").toString()),
                scope = scope,
                network = MutableStateFlow(network),
                onError = { _, error -> if (error != null) errors.add(error) },
                onBuffering = { _, _ -> },
                isSelected = { true },
                onRecovery = { _, state -> phases.add(state) }
            )
            try {
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
            } finally {
                try {
                    session.stop()
                } finally {
                    scope.cancel()
                }
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