package com.yourapp.iptv.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.yourapp.iptv.data.diagnostics.DiagnosticFailures
import com.yourapp.iptv.domain.diagnostics.*
import kotlinx.coroutines.CancellationException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PlaybackDiagnostics {
    fun record(error: PlaybackException, source: String? = null) {
        val (reason,status) = try { DiagnosticFailures.classify(error) } catch (_: CancellationException) { return }
        val http = generateSequence<Throwable>(error) { it.cause }.take(8)
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode ?: status
        val issue = when {
            http != null -> DiagnosticIssue.HTTP
            error.errorCode in setOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) -> DiagnosticIssue.DECODER
            reason == DiagnosticIssue.INTERNAL -> DiagnosticIssue.PLAYBACK
            else -> reason
        }
        DiagnosticJournal.record(DiagnosticArea.PLAYBACK,issue,source,error.errorCode,http)
    }
}