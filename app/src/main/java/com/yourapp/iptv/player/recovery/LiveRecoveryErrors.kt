package com.yourapp.iptv.player.recovery

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.io.FileNotFoundException
import java.io.IOException
import javax.net.ssl.SSLException

@androidx.annotation.OptIn(UnstableApi::class)
internal object LiveRecoveryErrors {
    fun retryable(error: PlaybackException): Boolean {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
        if (causes.any { it is SSLException || it is SecurityException || it is FileNotFoundException }) return false
        val http = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (http != null) return http.responseCode in setOf(408, 500, 502, 503, 504)
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> true
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> causes.any { it is IOException }
            else -> false
        }
    }
}