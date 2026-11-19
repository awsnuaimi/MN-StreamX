package mnstudio.apps.mnstreamx.domain.performance

/** Per-attempt numeric milestones. Late callbacks from retired sources cannot finish a new attempt. */
internal class LiveStartupTrace(
    private val recorder: PerformanceRecorder = PerformanceJournal.recorder,
    private val onHttpOpen: () -> Unit = {}
) {
    private val startedAt = recorder.now()
    private val pending = linkedSetOf(
        PerformanceStage.LIVE_HTTP_OPEN,
        PerformanceStage.LIVE_FIRST_BYTE,
        PerformanceStage.LIVE_TRACKS_READY,
        PerformanceStage.LIVE_PLAYER_READY,
        PerformanceStage.LIVE_ATTEMPT_FIRST_FRAME
    )
    private var closed = false

    @Synchronized fun reached(stage: PerformanceStage) {
        if (closed || !pending.remove(stage)) return
        recorder.recordSince(stage, startedAt)
        if (stage == PerformanceStage.LIVE_HTTP_OPEN) onHttpOpen()
    }

    @Synchronized fun close(outcome: PerformanceOutcome = PerformanceOutcome.CANCELLED) {
        require(outcome != PerformanceOutcome.COMPLETE)
        if (closed) return
        closed = true
        pending.forEach { recorder.recordSince(it, startedAt, outcome) }
        pending.clear()
    }
}