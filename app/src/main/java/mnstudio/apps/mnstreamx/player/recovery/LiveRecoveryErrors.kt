package mnstudio.apps.mnstreamx.player.recovery

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.net.ssl.SSLException

@androidx.annotation.OptIn(UnstableApi::class)
internal object LiveRecoveryErrors {
    data class Decision(val retryable: Boolean, val retryAfterMs: Long = 0, val httpStatus: Int? = null)

    private const val PROVIDER_STATUS_458 = 458
    private val retryableHttpStatuses = setOf(404, 408, 429, PROVIDER_STATUS_458, 500, 502, 503, 504)

    fun classify(error: PlaybackException, hasPlayedOrRetried: Boolean = false): Decision {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
        val http = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (http != null) {
            val temporary = http.responseCode in retryableHttpStatuses
            return Decision(temporary, if (temporary) retryAfterMs(http) else 0L, http.responseCode)
        }
        if (causes.any { it is SSLException || it is SecurityException || it is FileNotFoundException }) {
            return Decision(false)
        }
        val retryable = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            // A restarted live source can briefly serve an incomplete playlist or media segment.
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> true
            // A maintenance page can arrive with HTTP 200 while the same live source restarts.
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                hasPlayedOrRetried && causes.any { it is UnrecognizedInputFormatException }
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> causes.any { it is IOException }
            PlaybackException.ERROR_CODE_UNSPECIFIED ->
                (hasPlayedOrRetried && error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_SOURCE) ||
                    causes.any {
                        it is EOFException || it is SocketTimeoutException || it is SocketException ||
                            it is UnknownHostException
                    }
            else -> false
        }
        return Decision(retryable)
    }

    fun shouldDeferHttpRetry(error: HttpDataSource.InvalidResponseCodeException): Boolean {
        val status = error.responseCode
        // Schedule the nonstandard provider response at the same level as other live retries.
        return status == 429 || status == PROVIDER_STATUS_458 ||
            (status in retryableHttpStatuses && retryAfterMs(error) > 0L)
    }

    fun retryAfterMs(
        error: HttpDataSource.InvalidResponseCodeException,
        wallTimeMs: Long = System.currentTimeMillis()
    ): Long {
        val value = error.headerFields.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
            ?.value?.firstOrNull()?.trim() ?: return 0
        value.toLongOrNull()?.let { return it.coerceIn(0, Long.MAX_VALUE / 2_000) * 1_000 }
        return try {
            val retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            (retryAt.toEpochMilli() - wallTimeMs).coerceAtLeast(0)
        } catch (_: DateTimeParseException) {
            0
        }
    }
}