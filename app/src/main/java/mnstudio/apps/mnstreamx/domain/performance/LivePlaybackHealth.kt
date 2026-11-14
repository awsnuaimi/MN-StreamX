package mnstudio.apps.mnstreamx.domain.performance

import java.util.ArrayDeque
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase

/** Non-identifying, process-local live TV telemetry. No URLs or channel IDs are retained. */
data class LivePlaybackHealthSummary(
    val durationMs: Long,
    val firstFrameMs: Long?,
    val bufferingEvents: Int,
    val bufferingMs: Long,
    val longestBufferMs: Long,
    val recoveryAttempts: Int,
    val playerErrors: Int,
    val finished: Boolean,
    val interruptionCount: Int = 0,
    val interruptions: List<LivePlaybackInterruption> = emptyList()
)

enum class LivePlaybackInterruptionOutcome { ACTIVE, RECOVERED, FAILED, STOPPED }

data class LivePlaybackInterruption(
    val durationMs: Long,
    val networkWaitMs: Long,
    val retryAfterWaitMs: Long,
    val retryDelayMs: Long,
    val loadingMs: Long,
    val attempts: Int,
    val outcome: LivePlaybackInterruptionOutcome,
    val connectingMs: Long = 0L
)

/** Tracks one selected channel across its automatic reconnect attempts. No timers or jobs. */
internal class LivePlaybackHealthSession(
    private val nowMs: () -> Long,
    private val startedAt: Long = nowMs()
) {
    private var endedAt: Long? = null
    private var firstFrameAt: Long? = null
    private var bufferingSince: Long? = null
    private var bufferingEvents = 0
    private var bufferingMs = 0L
    private var longestBufferMs = 0L
    private var recoveryAttempts = 0
    private var playerErrors = 0
    private var playbackConfirmed = false
    private var interruptionCount = 0
    private var interruption: Interruption? = null
    private val recentInterruptions = ArrayDeque<LivePlaybackInterruption>()

    @Synchronized fun buffering() {
        if (endedAt != null || bufferingSince != null) return
        val at = nowMs()
        bufferingSince = at
        if (bufferingEvents < Int.MAX_VALUE) bufferingEvents++
        startInterruption(at)
    }

    @Synchronized fun bufferEnded() {
        if (endedAt != null) return
        closeBuffer(nowMs())
    }

    @Synchronized fun firstFrame() {
        if (endedAt != null || firstFrameAt != null) return
        firstFrameAt = nowMs()
    }

    @Synchronized fun retry() {
        if (endedAt != null) return
        if (recoveryAttempts < Int.MAX_VALUE) recoveryAttempts++
        interruption?.let { if (it.attempts < Int.MAX_VALUE) it.attempts++ }
    }

    @Synchronized fun error(retryAfterMs: Long = 0L, playbackAllowed: Boolean = true) {
        if (endedAt != null) return
        if (playerErrors < Int.MAX_VALUE) playerErrors++
        val at = nowMs()
        closeBuffer(at)
        if (playbackAllowed) startInterruption(at)
        interruption?.let {
            it.advanceTo(at)
            it.retryNotBeforeMs = at + retryAfterMs.coerceIn(0L, MAX_DURATION_MS)
        }
    }

    @Synchronized fun recoveryChanged(phase: LiveRecoveryPhase) {
        if (endedAt != null || !playbackConfirmed) return
        val at = nowMs()
        if (phase == LiveRecoveryPhase.WAITING_NETWORK) startInterruption(at)
        interruption?.let {
            it.advanceTo(at)
            it.phase = phase
            if (phase == LiveRecoveryPhase.CONNECTING) it.httpOpen = false
        }
        if (phase == LiveRecoveryPhase.FAILED) {
            recordInterruption(at, LivePlaybackInterruptionOutcome.FAILED)
        }
    }

    @Synchronized fun httpOpened() {
        val current = interruption ?: return
        if (current.phase != LiveRecoveryPhase.CONNECTING || current.httpOpen) return
        current.advanceTo(nowMs())
        current.httpOpen = true
    }

    @Synchronized fun playbackStalled() {
        if (endedAt == null && playbackConfirmed && interruption == null) startInterruption(nowMs())
    }

    @Synchronized fun playbackAdvanced() {
        if (endedAt != null || (playbackConfirmed && interruption == null)) return
        playbackConfirmed = true
        recordInterruption(nowMs(), LivePlaybackInterruptionOutcome.RECOVERED)
    }

    @Synchronized fun playbackSuspended() {
        if (endedAt != null) return
        val at = nowMs()
        closeBuffer(at)
        recordInterruption(at, LivePlaybackInterruptionOutcome.STOPPED)
        playbackConfirmed = false
    }

    @Synchronized fun finish() {
        if (endedAt != null) return
        val at = nowMs()
        closeBuffer(at)
        recordInterruption(at, LivePlaybackInterruptionOutcome.STOPPED)
        endedAt = at
    }

    @Synchronized fun snapshot(): LivePlaybackHealthSummary {
        val end = endedAt ?: nowMs()
        val pending = bufferingSince?.let { elapsed(it, end) } ?: 0L
        return LivePlaybackHealthSummary(
            durationMs = elapsed(startedAt, end),
            firstFrameMs = firstFrameAt?.let { elapsed(startedAt, it) },
            bufferingEvents = bufferingEvents,
            bufferingMs = (bufferingMs + pending).coerceAtMost(MAX_DURATION_MS),
            longestBufferMs = maxOf(longestBufferMs, pending),
            recoveryAttempts = recoveryAttempts,
            playerErrors = playerErrors,
            finished = endedAt != null,
            interruptionCount = interruptionCount,
            interruptions = buildList {
                interruption?.let { add(it.snapshot(end, LivePlaybackInterruptionOutcome.ACTIVE)) }
                addAll(recentInterruptions.take(MAX_INTERRUPTION_DETAILS - size))
            }
        )
    }

    private fun startInterruption(at: Long) {
        if (!playbackConfirmed || interruption != null) return
        interruption = Interruption(at)
        if (interruptionCount < Int.MAX_VALUE) interruptionCount++
    }

    private fun recordInterruption(at: Long, outcome: LivePlaybackInterruptionOutcome) {
        val current = interruption ?: return
        interruption = null
        recentInterruptions.addFirst(current.snapshot(at, outcome))
        if (recentInterruptions.size > MAX_INTERRUPTION_DETAILS) recentInterruptions.removeLast()
    }

    private fun closeBuffer(at: Long) {
        val start = bufferingSince ?: return
        val duration = elapsed(start, at)
        bufferingMs = (bufferingMs + duration).coerceAtMost(MAX_DURATION_MS)
        longestBufferMs = maxOf(longestBufferMs, duration)
        bufferingSince = null
    }

    private fun elapsed(start: Long, end: Long) = (end - start).coerceIn(0L, MAX_DURATION_MS)

    private class Interruption(private val startedAt: Long) {
        var phase = LiveRecoveryPhase.NONE
        var retryNotBeforeMs = 0L
        var attempts = 0
        var httpOpen = false
        private var updatedAt = startedAt
        private var networkWaitMs = 0L
        private var retryAfterWaitMs = 0L
        private var retryDelayMs = 0L
        private var loadingMs = 0L
        private var connectingMs = 0L

        fun advanceTo(at: Long) {
            val end = at.coerceIn(updatedAt, startedAt + MAX_DURATION_MS)
            val duration = end - updatedAt
            when (phase) {
                LiveRecoveryPhase.WAITING_NETWORK -> networkWaitMs += duration
                LiveRecoveryPhase.SCHEDULED -> {
                    val serverDelay = (retryNotBeforeMs - updatedAt).coerceIn(0L, duration)
                    retryAfterWaitMs += serverDelay
                    retryDelayMs += duration - serverDelay
                }
                LiveRecoveryPhase.CONNECTING -> if (httpOpen) loadingMs += duration else connectingMs += duration
                else -> loadingMs += duration
            }
            updatedAt = end
        }

        fun snapshot(at: Long, outcome: LivePlaybackInterruptionOutcome): LivePlaybackInterruption {
            val end = at.coerceIn(updatedAt, startedAt + MAX_DURATION_MS)
            val pending = end - updatedAt
            val network = if (phase == LiveRecoveryPhase.WAITING_NETWORK) pending else 0L
            val waiting = if (phase == LiveRecoveryPhase.SCHEDULED) pending else 0L
            val server = (retryNotBeforeMs - updatedAt).coerceIn(0L, waiting)
            val connecting = if (phase == LiveRecoveryPhase.CONNECTING && !httpOpen) pending else 0L
            return LivePlaybackInterruption(
                end - startedAt,
                networkWaitMs + network,
                retryAfterWaitMs + server,
                retryDelayMs + waiting - server,
                loadingMs + pending - network - waiting - connecting,
                attempts,
                outcome,
                connectingMs + connecting
            )
        }
    }

    companion object {
        const val MAX_DURATION_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_INTERRUPTION_DETAILS = 6
    }
}

/** Fixed eight-session window with bounded interruption history per session. */
internal class LivePlaybackHealthRecorder(private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val sessions = ArrayDeque<LivePlaybackHealthSession>()

    @Synchronized fun begin(): LivePlaybackHealthSession {
        val session = LivePlaybackHealthSession(nowMs)
        sessions.addFirst(session)
        while (sessions.size > LIMIT) sessions.removeLast()
        return session
    }

    @Synchronized fun snapshot(): List<LivePlaybackHealthSummary> = sessions.map { it.snapshot() }
    @Synchronized fun clear() = sessions.clear()

    companion object { const val LIMIT = 8 }
}

internal object LivePlaybackHealthJournal {
    private val recorder = LivePlaybackHealthRecorder()
    fun begin() = recorder.begin()
    fun snapshot(): List<LivePlaybackHealthSummary> = recorder.snapshot()
    fun clear() = recorder.clear()
}