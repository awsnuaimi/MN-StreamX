package mnstudio.apps.mnstreamx.player.recovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState

internal sealed interface PlaybackConnection {
    data object Unknown : PlaybackConnection
    data object Offline : PlaybackConnection
    data class Online(val networkHandle: Long, val validated: Boolean = false) : PlaybackConnection
}

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
    private var connection: PlaybackConnection = PlaybackConnection.Unknown
    private var attempts = 0
    private var retryPending = false
    private var retryNotBeforeMs = 0L
    private var pendingRetryAtMs: Long? = null
    private var networkChangedAtMs: Long? = null
    private var expediteNextRetry = false
    private var timer: Job? = null
    private var timerDeadlineMs = 0L
    private var stablePlayback: Job? = null
    var state = LiveRecoveryState()
        private set

    fun reset() {
        close()
        active = true
    }

    fun close() {
        active = false
        cancelTimers()
        attempts = 0
        retryPending = false
        retryNotBeforeMs = 0
        pendingRetryAtMs = null
        networkChangedAtMs = null
        expediteNextRetry = false
        publish(LiveRecoveryPhase.NONE)
    }

    fun suspendForBackground() {
        connection = PlaybackConnection.Unknown
        if (!active) return
        active = false
        if (state.phase == LiveRecoveryPhase.CONNECTING) {
            // Releasing the player interrupts the active attempt before it can report progress.
            retryPending = true
            pendingRetryAtMs = nowMs()
        }
        cancelTimers()
        networkChangedAtMs = null
        expediteNextRetry = false
    }

    /** Returns true when recovery still owns the source or a permanent error needs user action. */
    fun resume(): Boolean {
        active = true
        playbackPermissionChanged()
        return retryPending || state.phase == LiveRecoveryPhase.FAILED
    }

    fun playbackPermissionChanged() {
        if (!active) return
        if (!canRetry()) {
            suspendRecovery()
        } else if (retryPending && timer == null) {
            schedule(expedited = expediteNextRetry && recentlyChangedNetwork())
        }
    }

    fun error(retryable: Boolean, retryAfterMs: Long = 0L) {
        if (!active) return
        cancelTimers()
        pendingRetryAtMs = null
        retryPending = retryable
        if (retryable) {
            retryNotBeforeMs = nowMs() + retryAfterMs
            playbackPermissionChanged()
        } else {
            publish(LiveRecoveryPhase.FAILED)
        }
    }

    private fun suspendRecovery() {
        cancelTimers()
        if (state.phase != LiveRecoveryPhase.FAILED) publish(LiveRecoveryPhase.NONE)
    }

    fun networkChanged(value: PlaybackConnection) {
        val previous = connection
        if (value == previous) return
        connection = value
        if (!active || !canRetry()) return

        when (value) {
            PlaybackConnection.Offline -> {
                stablePlayback?.cancel()
                stablePlayback = null
                if (state.isRecovering || timer != null) {
                    waitForNetwork()
                }
            }
            is PlaybackConnection.Online -> {
                val usableChange = previous == PlaybackConnection.Offline ||
                    (previous is PlaybackConnection.Online && (previous.networkHandle != value.networkHandle ||
                        (!previous.validated && value.validated)))
                if (usableChange || state.phase == LiveRecoveryPhase.WAITING_NETWORK) {
                    networkChangedAtMs = nowMs()
                    expediteNextRetry = true
                    onUsableNetworkChange()
                }
            }
            PlaybackConnection.Unknown -> Unit
        }
    }

    private fun onUsableNetworkChange() {
        when (state.phase) {
            LiveRecoveryPhase.WAITING_NETWORK, LiveRecoveryPhase.SCHEDULED -> schedule(expedited = true)
            LiveRecoveryPhase.CONNECTING, LiveRecoveryPhase.NONE -> {
                if (timer != null) watchForProgress(NETWORK_HANDOVER_TIMEOUT_MS)
            }
            else -> Unit
        }
    }

    fun waitIfOffline(): Boolean {
        if (!active || !canRetry() || connection != PlaybackConnection.Offline) return false
        waitForNetwork()
        return true
    }

    private fun waitForNetwork() {
        cancelTimers()
        retryPending = true
        publish(LiveRecoveryPhase.WAITING_NETWORK)
        // A missing default-network callback must not leave a live channel waiting forever.
        scheduleRetryAt(nowMs() + maxOf(OFFLINE_RETRY_DELAY_MS, retryBackoffMs()))
    }

    fun awaitingPlayback() {
        if (!active || !canRetry()) return
        stablePlayback?.cancel()
        stablePlayback = null
        when (state.phase) {
            LiveRecoveryPhase.NONE -> if (!waitIfOffline() && timer == null) {
                val timeoutMs = if (recentlyChangedNetwork()) NETWORK_HANDOVER_TIMEOUT_MS else STALL_TIMEOUT_MS
                watchForProgress(timeoutMs)
            }
            LiveRecoveryPhase.CONNECTING -> if (timer == null) watchForProgress(CONNECT_TIMEOUT_MS)
            else -> Unit
        }
    }

    /** Only playback progress can complete an attempt. Media3 can report READY before playback. */
    fun playbackAdvanced() {
        if (!active || !canRetry() ||
            (state.phase != LiveRecoveryPhase.NONE && state.phase != LiveRecoveryPhase.CONNECTING)) return
        cancelTimer()
        // Playback progress supersedes an offline report if Android missed the return event.
        if (state.phase == LiveRecoveryPhase.CONNECTING && connection == PlaybackConnection.Offline) {
            connection = PlaybackConnection.Unknown
        }
        publish(LiveRecoveryPhase.NONE)
        if (attempts > 0 && stablePlayback == null && connection != PlaybackConnection.Offline) {
            stablePlayback = scope.launch {
                delay(STABLE_PLAYBACK_MS)
                stablePlayback = null
                attempts = 0
                retryNotBeforeMs = 0
                publish(LiveRecoveryPhase.NONE)
            }
        }
    }

    private fun schedule(expedited: Boolean) {
        if (connection == PlaybackConnection.Offline) {
            waitForNetwork()
            return
        }
        val backoffMs = if (expedited) NETWORK_RETRY_DELAY_MS else retryBackoffMs()
        val deadlineMs = nowMs() + backoffMs
        val savedDeadlineMs = pendingRetryAtMs
        val retryAt = if (expedited && savedDeadlineMs != null) {
            minOf(deadlineMs, savedDeadlineMs)
        } else {
            savedDeadlineMs ?: deadlineMs
        }
        publish(LiveRecoveryPhase.SCHEDULED, nextAttemptNumber())
        scheduleRetryAt(retryAt)
    }

    private fun nextAttemptNumber(): Int = attempts + if (attempts == Int.MAX_VALUE) 0 else 1

    private fun retryBackoffMs(): Long = RETRY_DELAYS_MS[attempts.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]

    private fun scheduleRetryAt(deadlineMs: Long) {
        val retryAtMs = maxOf(deadlineMs, retryNotBeforeMs)
        runAt(retryAtMs) {
            attempts = nextAttemptNumber()
            retryPending = false
            pendingRetryAtMs = null
            expediteNextRetry = false
            publish(LiveRecoveryPhase.CONNECTING)
            watchForProgress(CONNECT_TIMEOUT_MS)
            retry()
        }
        pendingRetryAtMs = timerDeadlineMs
    }

    private fun recentlyChangedNetwork(): Boolean {
        val changedAt = networkChangedAtMs ?: return false
        return nowMs() - changedAt < NETWORK_HANDOVER_WINDOW_MS
    }

    private fun watchForProgress(timeoutMs: Long) {
        runAt(nowMs() + timeoutMs, onTimeout)
    }

    private fun runAt(deadlineMs: Long, action: () -> Unit) {
        if (timer != null && timerDeadlineMs <= deadlineMs) return
        cancelTimer()
        timerDeadlineMs = deadlineMs
        timer = scope.launch {
            delay((deadlineMs - nowMs()).coerceAtLeast(1L))
            timer = null
            if (canRetry()) action() else suspendRecovery()
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    private fun cancelTimers() {
        cancelTimer()
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
        private const val NETWORK_HANDOVER_TIMEOUT_MS = 8_000L
        private const val NETWORK_HANDOVER_WINDOW_MS = 15_000L
        private const val NETWORK_RETRY_DELAY_MS = 1_000L
        private const val OFFLINE_RETRY_DELAY_MS = 15_000L
        private val RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 7_000L, 15_000L, 30_000L)
    }
}