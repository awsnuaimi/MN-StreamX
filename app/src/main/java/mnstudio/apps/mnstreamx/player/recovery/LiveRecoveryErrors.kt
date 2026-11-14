package mnstudio.apps.mnstreamx.player.recovery

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.io.FileNotFoundException
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.net.ssl.SSLException

@androidx.annotation.OptIn(UnstableApi::class)
internal object LiveRecoveryErrors {
    data class Decision(val retryable: Boolean, val retryAfterMs: Long = 0)

    fun classify(error: PlaybackException): Decision {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
        val http = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (http != null) {
            val temporary = http.responseCode in setOf(404, 408, 429, 500, 502, 503, 504)
            return Decision(temporary, if (temporary) retryAfterMs(http) else 0L)
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
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> causes.any { it is IOException }
            else -> false
        }
        return Decision(retryable)
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