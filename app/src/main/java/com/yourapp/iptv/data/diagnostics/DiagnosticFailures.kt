package com.yourapp.iptv.data.diagnostics

import com.yourapp.iptv.data.network.HttpResponseFailure
import com.yourapp.iptv.data.network.ResponseLimitFailure
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.PlaylistLoadException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

internal object DiagnosticFailures {
    fun classify(error: Throwable): Pair<DiagnosticIssue,Int?> {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        if (causes.any { it is CancellationException }) throw CancellationException("Diagnostic operation cancelled")
        causes.filterIsInstance<HttpResponseFailure>().firstOrNull()?.let { return DiagnosticIssue.HTTP to it.status }
        val issue = when {
            causes.any { it is UnknownHostException } -> DiagnosticIssue.DNS
            causes.any { it is SocketTimeoutException || it is InterruptedIOException } -> DiagnosticIssue.TIMEOUT
            causes.any { it is SSLException } -> DiagnosticIssue.TLS
            causes.any { it is ResponseLimitFailure } -> DiagnosticIssue.LIMIT
            causes.any { it is com.google.gson.JsonParseException || it is com.google.gson.stream.MalformedJsonException } -> DiagnosticIssue.INVALID_RESPONSE
            causes.any { it is IOException && it !is PlaylistLoadException } -> DiagnosticIssue.CONNECTION
            causes.any { it is PlaylistLoadException } -> DiagnosticIssue.INVALID_RESPONSE
            causes.any { it is IOException } -> DiagnosticIssue.CONNECTION
            causes.any { it is IllegalArgumentException || it is IllegalStateException } -> DiagnosticIssue.INVALID_RESPONSE
            else -> DiagnosticIssue.INTERNAL
        }
        return issue to null
    }
    fun record(area: DiagnosticArea, error: Throwable, source: String? = null) {
        val (issue,status) = classify(error)
        DiagnosticJournal.record(area,issue,source,httpStatus = status)
    }
}