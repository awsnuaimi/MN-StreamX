package mnstudio.apps.mnstreamx.domain.performance

import java.util.ArrayDeque

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
    val startupBufferingEvents: Int = 0,
    val startupBufferingMs: Long = 0,
    val rebufferingEvents: Int = 0,
    val rebufferingMs: Long = 0,
    val longestRebufferMs: Long = 0
)

/** Tracks one selected channel across its automatic reconnect attempts. No timers or jobs. */
internal class LivePlaybackHealthSession(
    private val nowMs: () -> Long,
    private val startedAt: Long = nowMs()
) {
    private var endedAt: Long? = null
    private var firstFrameAt: Long? = null
    private var bufferingSince: Long? = null
    private var bufferingIsStartup = false
    private var bufferingEvents = 0
    private var bufferingMs = 0L
    private var longestBufferMs = 0L
    private var startupBufferingEvents = 0
    private var startupBufferingMs = 0L
    private var rebufferingEvents = 0
    private var rebufferingMs = 0L
    private var longestRebufferMs = 0L
    private var recoveryAttempts = 0
    private var playerErrors = 0

    @Synchronized fun buffering() {
        if (endedAt != null || bufferingSince != null) return
        bufferingSince = nowMs()
        bufferingIsStartup = firstFrameAt == null
        if (bufferingEvents < Int.MAX_VALUE) bufferingEvents++
        if (bufferingIsStartup) {
            if (startupBufferingEvents < Int.MAX_VALUE) startupBufferingEvents++
        } else if (rebufferingEvents < Int.MAX_VALUE) rebufferingEvents++
    }

    @Synchronized fun bufferEnded() {
        if (endedAt != null) return
        closeBuffer(nowMs())
    }

    /** Returns the selection-to-first-frame latency once; retries cannot create false 0 ms samples. */
    @Synchronized fun firstFrame(): Long? {
        if (endedAt != null || firstFrameAt != null) return null
        val at = nowMs()
        firstFrameAt = at
        return elapsed(startedAt, at)
    }

    @Synchronized fun retry() {
        if (endedAt == null && recoveryAttempts < Int.MAX_VALUE) recoveryAttempts++
    }

    @Synchronized fun error() {
        if (endedAt != null) return
        if (playerErrors < Int.MAX_VALUE) playerErrors++
        closeBuffer(nowMs())
    }

    @Synchronized fun finish() {
        if (endedAt != null) return
        val at = nowMs()
        closeBuffer(at)
        endedAt = at
    }

    @Synchronized fun snapshot(): LivePlaybackHealthSummary {
        val end = endedAt ?: nowMs()
        val pending = bufferingSince?.let { elapsed(it, end) } ?: 0L
        val pendingStartup = if (bufferingIsStartup) pending else 0L
        val pendingRebuffer = if (bufferingIsStartup) 0L else pending
        return LivePlaybackHealthSummary(
            durationMs = elapsed(startedAt, end),
            firstFrameMs = firstFrameAt?.let { elapsed(startedAt, it) },
            bufferingEvents = bufferingEvents,
            bufferingMs = (bufferingMs + pending).coerceAtMost(MAX_DURATION_MS),
            longestBufferMs = maxOf(longestBufferMs, pending),
            recoveryAttempts = recoveryAttempts,
            playerErrors = playerErrors,
            finished = endedAt != null,
            startupBufferingEvents = startupBufferingEvents,
            startupBufferingMs = saturatedAdd(startupBufferingMs, pendingStartup),
            rebufferingEvents = rebufferingEvents,
            rebufferingMs = saturatedAdd(rebufferingMs, pendingRebuffer),
            longestRebufferMs = maxOf(longestRebufferMs, pendingRebuffer)
        )
    }

    private fun closeBuffer(at: Long) {
        val start = bufferingSince ?: return
        val duration = elapsed(start, at)
        bufferingMs = saturatedAdd(bufferingMs, duration)
        if (bufferingIsStartup) startupBufferingMs = saturatedAdd(startupBufferingMs, duration)
        else {
            rebufferingMs = saturatedAdd(rebufferingMs, duration)
            longestRebufferMs = maxOf(longestRebufferMs, duration)
        }
        longestBufferMs = maxOf(longestBufferMs, duration)
        bufferingSince = null
        bufferingIsStartup = false
    }

    private fun saturatedAdd(left: Long, right: Long) = (left + right).coerceAtMost(MAX_DURATION_MS)

    private fun elapsed(start: Long, end: Long) = (end - start).coerceIn(0L, MAX_DURATION_MS)

    companion object { const val MAX_DURATION_MS = 7L * 24 * 60 * 60 * 1000 }
}

/** Fixed eight-session window; playback callbacks only update primitive counters. */
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