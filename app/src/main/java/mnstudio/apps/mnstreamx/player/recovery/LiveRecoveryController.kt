package mnstudio.apps.mnstreamx.player.recovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState

internal enum class PlaybackConnection { UNKNOWN, ONLINE, OFFLINE }

/** All events run on the player application thread. Only one retry or watchdog is pending. */
internal class LiveRecoveryController(
    private val scope: CoroutineScope,
    private val onState: (LiveRecoveryState) -> Unit,
    private val retry: () -> Unit,
    private val onTimeout: () -> Unit,
    private val canRetry: () -> Boolean,
    private val nowMs: () -> Long
) {
    private var active = false
    private var connection = PlaybackConnection.UNKNOWN
    private var attempts = 0
    private var retryNotBeforeMs = 0L
    private var timer: Job? = null
    private var stablePlayback: Job? = null
    var state = LiveRecoveryState()
        private set

    fun reset() {
        cancelTimers()
        active = true
        attempts = 0
        retryNotBeforeMs = 0
        publish(LiveRecoveryPhase.NONE)
    }

    fun close() {
        active = false
        cancelTimers()
        publish(LiveRecoveryPhase.NONE)
    }

    fun paused() {
        cancelTimers()
        if (state.phase != LiveRecoveryPhase.FAILED) publish(LiveRecoveryPhase.NONE)
    }

    fun error(retryable: Boolean, retryAfterMs: Long = 0L) {
        if (!active) return
        cancelTimers()
        if (retryable && canRetry()) {
            retryNotBeforeMs = nowMs() + retryAfterMs
            schedule()
        } else if (retryable) {
            paused()
        } else {
            publish(LiveRecoveryPhase.FAILED)
        }
    }

    fun networkChanged(value: PlaybackConnection) {
        if (value == connection) return
        connection = value
        if (!active || !canRetry()) return
        if (value == PlaybackConnection.OFFLINE) {
            stablePlayback?.cancel()
            stablePlayback = null
            if (state.isRecovering || timer != null) {
                cancelTimers()
                publish(LiveRecoveryPhase.WAITING_NETWORK)
            }
        } else if (state.phase == LiveRecoveryPhase.WAITING_NETWORK) {
            schedule()
        }
    }

    fun waitIfOffline(): Boolean {
        if (!active || !canRetry() || connection != PlaybackConnection.OFFLINE) return false
        cancelTimers()
        publish(LiveRecoveryPhase.WAITING_NETWORK)
        return true
    }

    fun awaitingPlayback() {
        if (!active || !canRetry()) return
        stablePlayback?.cancel()
        stablePlayback = null
        when (state.phase) {
            LiveRecoveryPhase.NONE -> if (!waitIfOffline() && timer == null) watchForProgress(STALL_TIMEOUT_MS)
            LiveRecoveryPhase.CONNECTING -> if (timer == null) watchForProgress(CONNECT_TIMEOUT_MS)
            else -> Unit
        }
    }

    /** Only playback progress can complete an attempt. Media3 can report READY before playback. */
    fun playbackAdvanced() {
        if (!active || !canRetry() ||
            (state.phase != LiveRecoveryPhase.NONE && state.phase != LiveRecoveryPhase.CONNECTING)) return
        timer?.cancel()
        timer = null
        publish(LiveRecoveryPhase.NONE)
        if (attempts > 0 && stablePlayback == null && connection != PlaybackConnection.OFFLINE) {
            stablePlayback = scope.launch {
                delay(STABLE_PLAYBACK_MS)
                stablePlayback = null
                attempts = 0
                retryNotBeforeMs = 0
                publish(LiveRecoveryPhase.NONE)
            }
        }
    }

    private fun schedule() {
        if (connection == PlaybackConnection.OFFLINE) {
            publish(LiveRecoveryPhase.WAITING_NETWORK)
            return
        }
        val nextAttempt = if (attempts == Int.MAX_VALUE) attempts else attempts + 1
        val backoff = RETRY_DELAYS_MS[attempts.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
        publish(LiveRecoveryPhase.SCHEDULED, nextAttempt)
        timer = scope.launch {
            delay(maxOf(backoff, retryNotBeforeMs - nowMs()))
            timer = null
            if (!canRetry()) {
                paused()
                return@launch
            }
            attempts = nextAttempt
            publish(LiveRecoveryPhase.CONNECTING)
            watchForProgress(CONNECT_TIMEOUT_MS)
            retry()
        }
    }

    private fun watchForProgress(timeoutMs: Long) {
        timer = scope.launch {
            delay(timeoutMs)
            timer = null
            if (canRetry()) onTimeout() else paused()
        }
    }

    private fun cancelTimers() {
        timer?.cancel()
        timer = null
        stablePlayback?.cancel()
        stablePlayback = null
    }

    private fun publish(phase: LiveRecoveryPhase, attempt: Int = attempts) {
        val next = LiveRecoveryState(phase, attempt)
        if (next != state) {
            state = next
            onState(next)
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val STALL_TIMEOUT_MS = 30_000L
        const val STABLE_PLAYBACK_MS = 15_000L
        private val RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 7_000L, 15_000L, 30_000L)
    }
}