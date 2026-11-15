package mnstudio.apps.mnstreamx.player.recovery

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.player.controller.LiveLoadErrorPolicy
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class LiveRecoveryErrorsTest {
    private fun error(code: Int, cause: Throwable? = null) = PlaybackException("Synthetic", cause, code)
    private val spec = DataSpec.Builder().setUri("https://unit.test/live").build()

    private fun response(status: Int, headers: Map<String, List<String>> = emptyMap()) =
        HttpDataSource.InvalidResponseCodeException(status, "Synthetic", null, headers, spec, byteArrayOf())

    private fun http(status: Int, headers: Map<String, List<String>> = emptyMap()): PlaybackException =
        error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, IOException(response(status, headers)))

    private fun retryable(error: PlaybackException) = LiveRecoveryErrors.classify(error).retryable

    @Test fun networkTimeoutsDnsFailuresAndExpiredLiveWindowsAreRecoverable() {
        assertTrue(retryable(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, UnknownHostException())))
        assertTrue(retryable(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, SocketTimeoutException())))
        assertTrue(retryable(error(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)))
        assertTrue(retryable(error(PlaybackException.ERROR_CODE_TIMEOUT)))
    }

    @Test fun onlySelectedTemporaryHttpStatusesAreRetried() {
        listOf(404, 408, 429, 458, 500, 502, 503, 504).forEach { assertTrue("HTTP $it", retryable(http(it))) }
        listOf(400, 401, 403, 410, 501).forEach { assertFalse("HTTP $it", retryable(http(it))) }
    }

    @Test fun retryAfterAcceptsSecondsAndHttpDate() {
        val decision = LiveRecoveryErrors.classify(http(429, mapOf("retry-after" to listOf("75"))))
        assertTrue(decision.retryable)
        assertEquals(75_000L, decision.retryAfterMs)
        val now = Instant.parse("2015-10-21T07:27:00Z").toEpochMilli()
        val date = response(503, mapOf("Retry-After" to listOf("Wed, 21 Oct 2015 07:28:00 GMT")))
        assertEquals(60_000L, LiveRecoveryErrors.retryAfterMs(date, now))
        assertEquals(0L, LiveRecoveryErrors.retryAfterMs(date, now + 61_000))
        assertEquals(0L, LiveRecoveryErrors.classify(http(429, mapOf("Retry-After" to listOf("invalid")))).retryAfterMs)
    }

    @Test fun media3DefersServerCooldownsToSessionRecovery() {
        val fallbackOptions = LoadErrorHandlingPolicy.FallbackOptions(2, 0, 2, 0)
        fun loadError(cause: IOException) = LoadErrorHandlingPolicy.LoadErrorInfo(
            LoadEventInfo(1, spec, 0), MediaLoadData(C.DATA_TYPE_MEDIA), cause, 2
        )
        val rateLimit = loadError(response(429))
        val cooldown = loadError(response(503, mapOf("Retry-After" to listOf("75"))))
        val providerResponse = loadError(response(458))
        val gatewayCooldown = loadError(response(502, mapOf("Retry-After" to listOf("120"))))
        val forbidden = loadError(response(403, mapOf("Retry-After" to listOf("120"))))
        val temporary = loadError(response(503))
        assertEquals(C.TIME_UNSET, LiveLoadErrorPolicy.getRetryDelayMsFor(rateLimit))
        assertEquals(C.TIME_UNSET, LiveLoadErrorPolicy.getRetryDelayMsFor(cooldown))
        assertEquals(C.TIME_UNSET, LiveLoadErrorPolicy.getRetryDelayMsFor(providerResponse))
        assertEquals(C.TIME_UNSET, LiveLoadErrorPolicy.getRetryDelayMsFor(gatewayCooldown))
        assertEquals(1_000L, LiveLoadErrorPolicy.getRetryDelayMsFor(forbidden))
        assertNull(LiveLoadErrorPolicy.getFallbackSelectionFor(fallbackOptions, providerResponse))
        assertNull(LiveLoadErrorPolicy.getFallbackSelectionFor(fallbackOptions, cooldown))
        assertEquals(1_000L, LiveLoadErrorPolicy.getRetryDelayMsFor(temporary))
        assertNotNull(LiveLoadErrorPolicy.getFallbackSelectionFor(fallbackOptions, temporary))
    }

    @Test fun certificatePermissionAndMissingFileErrorsCannotBeReclassifiedAsTransientNetworkFailures() {
        listOf(SSLHandshakeException("Synthetic"), SecurityException("Synthetic"), FileNotFoundException("Synthetic")).forEach {
            assertFalse(retryable(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, IOException(it))))
        }
    }

    @Test fun decoderAndUnknownFailuresNeedExplicitUserAction() {
        assertFalse(retryable(error(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)))
        assertFalse(retryable(error(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)))
        assertFalse(retryable(error(PlaybackException.ERROR_CODE_UNSPECIFIED)))
    }

    @Test fun anUnspecifiedErrorRecoversOnlyWhenItContainsAKnownTransportFailure() {
        val code = PlaybackException.ERROR_CODE_UNSPECIFIED
        listOf(EOFException(), SocketException(), SocketTimeoutException(), UnknownHostException()).forEach {
            assertTrue(retryable(error(code, IllegalStateException(it))))
        }
        listOf(IllegalStateException(), IOException(), SSLHandshakeException("Synthetic"), SecurityException()).forEach {
            assertFalse(retryable(error(code, it)))
        }
    }

    @Test fun theProviderResponseKeepsItsExplicitCooldown() {
        val decision = LiveRecoveryErrors.classify(http(458, mapOf("Retry-After" to listOf("120"))))
        assertTrue(decision.retryable)
        assertEquals(120_000L, decision.retryAfterMs)
        assertEquals(458, decision.httpStatus)
        val forbidden = LiveRecoveryErrors.classify(http(403, mapOf("Retry-After" to listOf("120"))))
        assertFalse(forbidden.retryable)
        assertEquals(403, forbidden.httpStatus)
        assertNull(LiveRecoveryErrors.classify(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)).httpStatus)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun aPreviouslyPlayingChannelKeepsTryingWithoutAServerCooldown() = runTest {
        val outageEndsAt = 16 * 60_000L
        val attempts = mutableListOf<Long>()
        val response = http(458)
        val decision = LiveRecoveryErrors.classify(response)
        lateinit var recovery: LiveRecoveryController
        recovery = LiveRecoveryController(
            scope = this,
            onState = {},
            retry = {
                attempts.add(testScheduler.currentTime)
                if (testScheduler.currentTime < outageEndsAt) {
                    recovery.error(decision.retryable, decision.retryAfterMs)
                } else {
                    recovery.playbackAdvanced()
                }
            },
            onTimeout = { fail("Every simulated request returns before the watchdog") },
            canRetry = { true },
            nowMs = { testScheduler.currentTime }
        )
        recovery.reset()
        try {
            recovery.playbackAdvanced()
            recovery.error(decision.retryable, decision.retryAfterMs)
            advanceTimeBy(outageEndsAt)
            runCurrent()
            assertTrue(attempts.size > 30)
            assertTrue(recovery.state.isRecovering)
            assertTrue(attempts.zipWithNext().all { (a, b) -> b - a in 1_000L..30_000L })

            advanceTimeBy(30_000)
            runCurrent()
            assertTrue(attempts.last() >= outageEndsAt)
            assertEquals(LiveRecoveryPhase.NONE, recovery.state.phase)
            val completedAttempts = attempts.size
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(completedAttempts, attempts.size)
        } finally {
            recovery.close()
        }
    }

    @Test fun liveManifestAndContainerParsingErrorsCanRecoverAfterTheSourceReturns() {
        val manifest = ParserException.createForMalformedManifest("Synthetic", null)
        val container = ParserException.createForMalformedContainer("Synthetic", EOFException())
        assertTrue(retryable(error(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, manifest)))
        assertTrue(retryable(error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, container)))
    }

    @Test fun unsupportedManifestAndContainerFeaturesStillNeedExplicitUserAction() {
        val manifest = ParserException.createForManifestWithUnsupportedFeature("Synthetic", null)
        val container = ParserException.createForUnsupportedContainerFeature("Synthetic")
        assertFalse(retryable(error(PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED, manifest)))
        assertFalse(retryable(error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, container)))
    }

    @Test fun interruptedIoCanRecoverButAnUnspecifiedFailureWithoutAnIoCauseCannot() {
        assertTrue(retryable(error(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, EOFException())))
        assertFalse(retryable(error(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
    }
}