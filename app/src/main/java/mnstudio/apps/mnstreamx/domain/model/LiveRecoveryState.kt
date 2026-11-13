package mnstudio.apps.mnstreamx.domain.model

enum class LiveRecoveryPhase { NONE, WAITING_NETWORK, SCHEDULED, CONNECTING, FAILED }

/** Session state only; no account, URL, or saved settings enter recovery status. */
data class LiveRecoveryState(
    val phase: LiveRecoveryPhase = LiveRecoveryPhase.NONE,
    val attempt: Int = 0
) {
    val isRecovering: Boolean get() = phase == LiveRecoveryPhase.WAITING_NETWORK ||
        phase == LiveRecoveryPhase.SCHEDULED || phase == LiveRecoveryPhase.CONNECTING
}