package mnstudio.apps.mnstreamx.domain.performance

/** Per-attempt numeric milestones. Late callbacks from retired sources cannot finish a new attempt. */
internal class LiveStartupTrace(private val recorder: PerformanceRecorder = PerformanceJournal.recorder) {
    private val startedAt = recorder.now()
    private val pending = linkedSetOf(PerformanceStage.LIVE_HTTP_OPEN,PerformanceStage.LIVE_FIRST_BYTE,
        PerformanceStage.LIVE_TRACKS_READY,PerformanceStage.LIVE_PLAYER_READY)
    private var closed = false

    @Synchronized fun reached(stage: PerformanceStage) {
        if (!closed && pending.remove(stage)) recorder.recordSince(stage,startedAt)
    }

    @Synchronized fun close(outcome: PerformanceOutcome = PerformanceOutcome.CANCELLED) {
        require(outcome != PerformanceOutcome.COMPLETE)
        if (closed) return
        closed = true
        pending.forEach { recorder.recordSince(it,startedAt,outcome) }
        pending.clear()
    }
}