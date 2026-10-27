package com.yourapp.iptv.domain.diagnostics

import com.yourapp.iptv.domain.update.UpdateIssue

enum class DiagnosticArea { CONNECTION, ACCOUNT, PLAYLIST, LIVE_CATALOG, MOVIE_CATALOG, SERIES_CATALOG, DETAILS, PLAYBACK, UPDATE, SYSTEM }
enum class DiagnosticStatus { PASS, NOTICE, FAIL }
enum class DiagnosticIssue {
    OK, NO_SOURCE, OFFLINE, UNVALIDATED, DNS, TIMEOUT, TLS, CONNECTION, HTTP,
    AUTH_REJECTED, ACCOUNT_INACTIVE, INVALID_RESPONSE, EMPTY_RESPONSE, LIMIT, REDIRECT,
    PLAYLIST_HEADER, CATEGORIES, PLAYBACK, DECODER, UPDATE, INTERNAL, CANCELLED, HISTORY_WRITE, HISTORY_QUEUE
}
enum class DiagnosticConnection { NONE, WIFI, ETHERNET, CELLULAR, VPN, OTHER, UNKNOWN }
enum class DiagnosticCodec { AVC, HEVC, AV1 }
enum class DiagnosticSource { NONE, M3U, XTREAM }

data class DiagnosticCheck(val area: DiagnosticArea, val status: DiagnosticStatus, val issue: DiagnosticIssue,
    val httpStatus: Int? = null, val elapsedMs: Long = 0, val count: Int? = null)

/** No URL, host, account name, title, exception message, token, or device identifier is accepted. */
data class DiagnosticEvent(val id: Long, val timeMs: Long, val area: DiagnosticArea, val issue: DiagnosticIssue,
    val sourceKey: String? = null, val code: Int? = null, val httpStatus: Int? = null, val update: UpdateIssue? = null)

data class DiagnosticSnapshot(
    val version: String, val sdk: Int, val abis: List<String>, val connection: DiagnosticConnection,
    val validated: Boolean?, val metered: Boolean?, val totalRam: Long?, val availableRam: Long?,
    val lowMemory: Boolean?, val heapUsed: Long, val heapLimit: Long,
    val diskAvailable: Long?, val diskTotal: Long?, val codecs: Set<DiagnosticCodec>?
)