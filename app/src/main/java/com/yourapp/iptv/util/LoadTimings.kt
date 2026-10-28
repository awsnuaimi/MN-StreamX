package com.yourapp.iptv.util

import com.yourapp.iptv.domain.performance.*

/** Bounded, process-local diagnostics. No URLs, account details, or channel names. */
internal object LoadTimings {
    enum class Stage(val metric: PerformanceStage) {
        CACHE_READ(PerformanceStage.CACHE_READ), REFRESH(PerformanceStage.REFRESH), STORE_WRITE(PerformanceStage.STORE_WRITE),
        XTREAM_ACCOUNT(PerformanceStage.XTREAM_ACCOUNT), XTREAM_LISTS(PerformanceStage.XTREAM_LISTS),
        ORGANIZE(PerformanceStage.ORGANIZE), CHANNEL_LOOKUP(PerformanceStage.CHANNEL_LOOKUP),
        FIRST_FRAME(PerformanceStage.FIRST_FRAME), CATALOG_READ(PerformanceStage.CATALOG_READ), HOME_READY(PerformanceStage.HOME_READY)
    }
    private val activity = PerformanceSpans(PerformanceJournal.recorder)

    fun snapshotMillis(): Map<Stage, Long> {
        val values = PerformanceJournal.snapshot().filter { it.screen == null }.associateBy { it.stage }
        return Stage.entries.mapNotNull { stage -> values[stage.metric]?.let { stage to it.lastMs } }.toMap()
    }

    fun beginActivity() = activity.start(PerformanceStage.ACTIVITY_HOME)
    fun activityTicket() = activity.currentTicket()
    fun homeDrawn(ticket: Long?) { activity.finish(ticket) }
    fun cancelActivity(ticket: Long?) { activity.finish(ticket,PerformanceOutcome.CANCELLED) }

    fun recordSince(stage: Stage, startNanos: Long) {
        PerformanceJournal.recorder.recordSince(stage.metric,startNanos)
    }

    suspend fun <T> measure(stage: Stage, block: suspend () -> T): T {
        return PerformanceJournal.measure(stage.metric,block)
    }
}