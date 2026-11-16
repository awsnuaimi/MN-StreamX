package mnstudio.apps.mnstreamx.domain.model

enum class LiveRecoveryPhase { NONE, WAITING_NETWORK, SCHEDULED, CONNECTING, FAILED }

/** Session state with the next scheduled attempt on the elapsed realtime clock, if available. */
data class LiveRecoveryState(
    val phase: LiveRecoveryPhase = LiveRecoveryPhase.NONE,
    val attempt: Int = 0,
    val retryAtElapsedRealtimeMs: Long? = null
) {
    val isRecovering: Boolean get() = phase == LiveRecoveryPhase.WAITING_NETWORK ||
        phase == LiveRecoveryPhase.SCHEDULED || phase == LiveRecoveryPhase.CONNECTING
}