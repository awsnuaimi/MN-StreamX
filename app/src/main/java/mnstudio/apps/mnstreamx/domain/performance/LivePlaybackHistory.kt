package mnstudio.apps.mnstreamx.domain.performance

import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase

enum class LivePlaybackInterruptionTrigger { BUFFERING, OUTPUT_STALLED, PLAYER_ERROR, NETWORK_WAIT }

/** Technical observations only. An active entry describes the last recorded state. */
data class LivePlaybackHistoryEntry(
    val startedAtMs: Long,
    val observedAtMs: Long,
    val trigger: LivePlaybackInterruptionTrigger,
    val phase: LiveRecoveryPhase,
    val httpOpened: Boolean,
    val interruption: LivePlaybackInterruption
) {
    companion object { const val LIMIT = 12 }
}

/** The key identifies one interruption in this process and is never persisted or exported. */
internal interface LivePlaybackHistorySink {
    fun record(key: Any, entry: LivePlaybackHistoryEntry, isStart: Boolean)
    fun clear()
}