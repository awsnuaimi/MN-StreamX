package mnstudio.apps.mnstreamx.player.recovery

import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.player.controller.PlayerController
import mnstudio.apps.mnstreamx.ui.component.player.PlayerPlaybackSession
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class LivePlaybackRecoveryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun theSameChannelRendersAudioAndVideoAfterShortDropsAndSourceMaintenance() {
        val source = SyntheticBroadcast(readBroadcast())
        MockWebServer().use { server ->
            server.dispatcher = source
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val channel = Channel(CHANNEL_ID, "Synthetic broadcast", streamUrl = "http://127.0.0.1:${server.port}$PATH")
            val frames = AtomicInteger()
            val probe = PlaybackProbe(frames)
            val scope = CoroutineScope(Dispatchers.Main.immediate)
            val session = onMain {
                PlayerPlaybackSession(
                    controller = PlayerController(instrumentation.targetContext),
                    initialChannel = channel,
                    scope = scope,
                    network = MutableStateFlow(PlaybackConnection.Online(1, validated = true)),
                    onError = { _, error -> if (error == HTTP_ERROR) probe.httpErrors++ },
                    onBuffering = { _, buffering -> probe.bufferingChanged(buffering) },
                    isSelected = { it.id == channel.id && it.streamUrl == channel.streamUrl },
                    onRecovery = { _, state ->
                        probe.phase = state.phase
                        probe.failed = probe.failed || state.phase == LiveRecoveryPhase.FAILED
                    }
                )
            }
            val observer = HandlerThread("LiveRecoveryFrameReader").apply { start() }
            try {
                ImageReader.newInstance(160, 96, ImageFormat.PRIVATE, 3).use { reader ->
                    reader.setOnImageAvailableListener({ input ->
                        input.acquireLatestImage()?.use { frames.incrementAndGet() }
                    }, Handler(observer.looper))
                    try {
                        val capture = { onMain { probe.sample(session.player) } }
                        onMain {
                            session.start()
                            checkNotNull(session.player).setVideoSurface(reader.surface)
                        }
                        runScenario(source, capture)
                    } finally {
                        try {
                            onMain {
                                try {
                                    session.stop()
                                } finally {
                                    scope.cancel()
                                }
                            }
                        } finally {
                            reader.setOnImageAvailableListener(null, null)
                            observer.quitSafely()
                            observer.join(2_000)
                        }
                    }
                }
            } finally {
                observer.quitSafely()
                scope.cancel()
            }
        }
    }

    private fun runScenario(source: SyntheticBroadcast, capture: () -> PlaybackSample) {
        val playedAttempts = mutableSetOf<String>()
        repeat(3) { index ->
            val output = awaitOutput("playback ${index + 1} from the short broadcast", playedAttempts, 45_000, capture)
            playedAttempts.add(checkNotNull(output.mediaId))
        }

        source.mode.set(SourceMode.MAINTENANCE)
        val interrupted = awaitSample("four failed requests while the source is unavailable", 140_000, capture) {
            it.httpErrors >= 4 && it.phase == LiveRecoveryPhase.SCHEDULED
        }
        assertTrue("The application must reach the local provider four times during the outage", source.rejected.get() >= 4)
        val restoredAt = SystemClock.elapsedRealtime()
        source.mode.set(SourceMode.RESTORED)
        val resumed = awaitOutput("new audio and video after source restoration", playedAttempts, 65_000, capture)
        var lastOutput = resumed
        var outputStalls = 0
        val steady = awaitSample("five further seconds on the recovered stream", 18_000, capture) {
            assertEquals("The recovered connection must remain selected", resumed.mediaId, it.mediaId)
            if (it.hasAdvancedSince(lastOutput)) {
                if (it.observedAtMs - lastOutput.observedAtMs >= 3_000L) outputStalls++
                lastOutput = it
            }
            it.positionMs - resumed.positionMs >= 5_000L && it.hasAdvancedSince(resumed)
        }

        val rebuffers = steady.bufferingEvents - resumed.bufferingEvents
        Log.i(
            LOG_TAG,
            "short_restarts=${playedAttempts.size - 1} maintenance_failures=${interrupted.httpErrors} " +
                "restore_to_av_ms=${resumed.observedAtMs - restoredAt} " +
                "post_restore_rebuffers=$rebuffers post_restore_av_stalls=$outputStalls " +
                "post_restore_progress_ms=${steady.positionMs - resumed.positionMs}"
        )
        assertEquals("The healthy source must resume without rebuffering again", 0, rebuffers)
        assertEquals("No further gap of three seconds or more in audio and video output", 0, outputStalls)
    }

    private fun awaitOutput(
        description: String,
        playedAttempts: Set<String>,
        timeoutMs: Long,
        capture: () -> PlaybackSample
    ): PlaybackSample {
        var baseline: PlaybackSample? = null
        return awaitSample(description, timeoutMs, capture) { sample ->
            val mediaId = sample.mediaId ?: return@awaitSample false
            if (mediaId in playedAttempts || !sample.playing) return@awaitSample false
            val previous = baseline
            if (previous?.mediaId != mediaId) {
                baseline = sample
                return@awaitSample false
            }
            sample.positionMs - previous.positionMs >= 500L && sample.hasAdvancedSince(previous)
        }
    }

    private fun awaitSample(
        description: String,
        timeoutMs: Long,
        capture: () -> PlaybackSample,
        matches: (PlaybackSample) -> Boolean
    ): PlaybackSample {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var sample: PlaybackSample
        do {
            sample = capture()
            assertFalse("Recovery failed while waiting for $description: $sample", sample.failed)
            val mediaId = sample.mediaId
            assertTrue("Unexpected channel while waiting for $description: $mediaId",
                mediaId == null || mediaId.startsWith("$CHANNEL_ID:"))
            if (matches(sample)) return sample
            Thread.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for $description. Last observation: $sample")
    }

    private fun readBroadcast(): ByteArray {
        // Synthetic H.264 color frames and a 500 Hz AAC tone. No host media or credentials.
        val encoded = instrumentation.context.assets.open("live_broadcast.ts.gz.b64").use { it.readBytes() }
        return GZIPInputStream(Base64.decode(encoded, Base64.DEFAULT).inputStream()).use { it.readBytes() }
    }

    private fun <T : Any> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }

    private enum class SourceMode { SHORT_BROADCAST, MAINTENANCE, RESTORED }

    private class SyntheticBroadcast(private val fullBroadcast: ByteArray) : Dispatcher() {
        val mode = AtomicReference(SourceMode.SHORT_BROADCAST)
        val rejected = AtomicInteger()
        // Packet-aligned prefix of the 16-second fixture with over four seconds of both tracks.
        private val shortBroadcast = fullBroadcast.copyOf(191 * 188)

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path != PATH) return MockResponse().setResponseCode(404)
            val bytes = when (mode.get()) {
                SourceMode.MAINTENANCE -> {
                    rejected.incrementAndGet()
                    return MockResponse().setResponseCode(458)
                }
                SourceMode.RESTORED -> fullBroadcast
                else -> shortBroadcast
            }
            return MockResponse().setHeader("Content-Type", "video/mp2t").setBody(Buffer().write(bytes))
        }
    }

    // Callbacks and sampling run on the application thread. The frame reader owns its atomic count.
    private class PlaybackProbe(private val surfaceFrames: AtomicInteger) {
        var httpErrors = 0
        var phase = LiveRecoveryPhase.NONE
        var failed = false
        private var buffering = false
        private var bufferingEvents = 0

        fun bufferingChanged(value: Boolean) {
            if (value && !buffering) bufferingEvents++
            buffering = value
        }

        fun sample(player: ExoPlayer?) = PlaybackSample(
            SystemClock.elapsedRealtime(),
            player?.currentMediaItem?.mediaId,
            player?.isPlaying == true,
            player?.currentPosition ?: 0,
            renderedBuffers(player?.audioDecoderCounters),
            renderedBuffers(player?.videoDecoderCounters),
            surfaceFrames.get(),
            httpErrors,
            bufferingEvents,
            phase,
            failed
        )

        private fun renderedBuffers(counters: DecoderCounters?): Int {
            counters?.ensureUpdated()
            return counters?.renderedOutputBufferCount ?: 0
        }
    }

    private data class PlaybackSample(
        val observedAtMs: Long,
        val mediaId: String?,
        val playing: Boolean,
        val positionMs: Long,
        val audioBuffers: Int,
        val videoBuffers: Int,
        val surfaceFrames: Int,
        val httpErrors: Int,
        val bufferingEvents: Int,
        val phase: LiveRecoveryPhase,
        val failed: Boolean
    ) {
        fun hasAdvancedSince(before: PlaybackSample): Boolean =
            playing && mediaId == before.mediaId && audioBuffers > before.audioBuffers &&
                videoBuffers > before.videoBuffers && surfaceFrames > before.surfaceFrames
    }

    private companion object {
        const val CHANNEL_ID = "synthetic-live"
        const val PATH = "/broadcast.ts"
        const val HTTP_ERROR = "ERROR_CODE_IO_BAD_HTTP_STATUS"
        const val LOG_TAG = "MNX.LiveRecoveryTest"
    }
}