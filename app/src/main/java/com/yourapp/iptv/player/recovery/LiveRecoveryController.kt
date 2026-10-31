package com.yourapp.iptv.player.recovery

import com.yourapp.iptv.domain.model.LiveRecoveryPhase
import com.yourapp.iptv.domain.model.LiveRecoveryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class PlaybackConnection { UNKNOWN, ONLINE, OFFLINE }

/** One selection owns one retry budget. Brief READY events cannot renew that budget. */
internal class LiveRecoveryController(private val scope: CoroutineScope,
    private val onState: (LiveRecoveryState) -> Unit,
    private val retry: () -> Unit, private val onTimeout: () -> Unit,
    private val canRetry: () -> Boolean = { true }) {
    private var active = false
    private var connection = PlaybackConnection.UNKNOWN
    private var failed = false
    private var playing = false
    private var attempts = 0
    private var revision = 0L
    private var retryJob: Job? = null
    private var timeoutJob: Job? = null
    private var stableJob: Job? = null
    var state = LiveRecoveryState()
        private set

    fun reset() {
        invalidate()
        active = true; failed = false; playing = false; attempts = 0
        publish(LiveRecoveryPhase.NONE)
    }

    fun close() {
        active = false; failed = false; playing = false
        invalidate(); publish(LiveRecoveryPhase.NONE)
    }

    /** Pausing cancels automatic work without starting playback again on a network event. */
    fun paused() {
        failed = false; playing = false
        invalidate(); publish(LiveRecoveryPhase.NONE)
    }

    fun error(retryable: Boolean) {
        if (!active) return
        invalidate(); playing = false; failed = retryable
        if (retryable) schedule() else publish(LiveRecoveryPhase.FAILED)
    }

    fun networkChanged(value: PlaybackConnection) {
        if (value == connection) return
        connection = value
        if (!active) return
        if (value == PlaybackConnection.OFFLINE) {
            stableJob?.cancel(); stableJob = null
            if (state.phase == LiveRecoveryPhase.CONNECTING || failed) {
                invalidate(); failed = true; schedule()
            }
        } else if (failed && state.phase == LiveRecoveryPhase.WAITING_NETWORK) schedule()
    }

    fun ready() {
        if (!active) return
        failed = false
        revision++; retryJob?.cancel(); retryJob = null
        timeoutJob?.cancel(); timeoutJob = null
        publish(LiveRecoveryPhase.NONE)
    }

    /** Bound a stalled initial load/rebuffer as well as the shorter reconnect attempts. */
    fun buffering() {
        if (!active || failed || state.phase == LiveRecoveryPhase.CONNECTING ||
            state.phase == LiveRecoveryPhase.EXHAUSTED || state.phase == LiveRecoveryPhase.FAILED) return
        stableJob?.cancel(); stableJob = null
        timeoutJob?.cancel()
        val ticket = ++revision
        timeoutJob = scope.launch {
            delay(STALL_TIMEOUT_MS)
            if (active && revision == ticket) { timeoutJob = null; onTimeout() }
        }
    }

    fun playing(value: Boolean) {
        if (!active) return
        playing = value
        stableJob?.cancel(); stableJob = null
        if (!value) return
        ready()
        if (attempts == 0 || connection == PlaybackConnection.OFFLINE) return
        val ticket = revision
        stableJob = scope.launch {
            delay(STABLE_PLAYBACK_MS)
            if (active && playing && revision == ticket && connection != PlaybackConnection.OFFLINE) {
                attempts = 0
                publish(LiveRecoveryPhase.NONE)
            }
        }
    }

    private fun schedule() {
        if (!active || !failed) return
        if (attempts >= MAX_ATTEMPTS) {
            failed = false; publish(LiveRecoveryPhase.EXHAUSTED); return
        }
        if (connection == PlaybackConnection.OFFLINE) {
            publish(LiveRecoveryPhase.WAITING_NETWORK); return
        }
        val ticket = ++revision
        val delayMs = RETRY_DELAYS_MS[attempts]
        publish(LiveRecoveryPhase.SCHEDULED, attempts + 1)
        retryJob = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMs)
            if (!active || ticket != revision || !failed || connection == PlaybackConnection.OFFLINE) return@launch
            retryJob = null
            if (!canRetry()) { paused(); return@launch }
            attempts++; failed = false
            publish(LiveRecoveryPhase.CONNECTING)
            timeoutJob = scope.launch(start = CoroutineStart.LAZY) {
                delay(CONNECT_TIMEOUT_MS)
                if (active && ticket == revision && state.phase == LiveRecoveryPhase.CONNECTING) {
                    timeoutJob = null
                    onTimeout()
                }
            }.also { it.start() }
            retry()
        }.also { it.start() }
    }

    private fun invalidate() {
        revision++
        retryJob?.cancel(); retryJob = null
        timeoutJob?.cancel(); timeoutJob = null
        stableJob?.cancel(); stableJob = null
    }

    private fun publish(phase: LiveRecoveryPhase, attempt: Int = attempts) {
        val next = LiveRecoveryState(phase, attempt, MAX_ATTEMPTS)
        if (next != state) { state = next; onState(next) }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val STALL_TIMEOUT_MS = 30_000L
        const val STABLE_PLAYBACK_MS = 15_000L
        private val RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 7_000L)
    }
}