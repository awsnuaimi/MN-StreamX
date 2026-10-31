package com.yourapp.iptv.player.recovery

import android.app.Application
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    private fun http(status: Int): PlaybackException {
        val cause = HttpDataSource.InvalidResponseCodeException(status, "Synthetic", null, emptyMap(),
            DataSpec.Builder().setUri("https://unit.test/live").build(), byteArrayOf())
        return error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, IOException(cause))
    }

    @Test fun networkTimeoutsDnsFailuresAndExpiredLiveWindowsAreRecoverable() {
        assertTrue(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, UnknownHostException())))
        assertTrue(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, SocketTimeoutException())))
        assertTrue(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)))
        assertTrue(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_TIMEOUT)))
    }

    @Test fun onlySelectedTemporaryHttpStatusesAreRetried() {
        listOf(408, 500, 502, 503, 504).forEach { assertTrue("HTTP $it", LiveRecoveryErrors.retryable(http(it))) }
        listOf(400, 401, 403, 404, 410, 429, 501).forEach { assertFalse("HTTP $it", LiveRecoveryErrors.retryable(http(it))) }
    }

    @Test fun certificatePermissionAndMissingFileErrorsCannotBeReclassifiedAsTransientNetworkFailures() {
        listOf(SSLHandshakeException("Synthetic"), SecurityException("Synthetic"), FileNotFoundException("Synthetic")).forEach {
            assertFalse(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, IOException(it))))
        }
    }

    @Test fun decoderAndUnknownFailuresNeedExplicitUserAction() {
        assertFalse(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)))
        assertFalse(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)))
        assertFalse(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_UNSPECIFIED)))
    }

    @Test fun interruptedIoCanRecoverButAnUnspecifiedFailureWithoutAnIoCauseCannot() {
        assertTrue(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, EOFException())))
        assertFalse(LiveRecoveryErrors.retryable(error(PlaybackException.ERROR_CODE_IO_UNSPECIFIED)))
    }
}