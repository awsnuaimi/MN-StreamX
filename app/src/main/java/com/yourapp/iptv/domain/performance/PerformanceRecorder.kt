package com.yourapp.iptv.domain.performance

import kotlinx.coroutines.CancellationException

internal class PerformanceRecorder(private val nanoTime: () -> Long = System::nanoTime) {
    private data class Key(val stage: PerformanceStage, val screen: PerformanceScreen?)
    private data class Sample(val ms: Long, val outcome: PerformanceOutcome, val clamped: Boolean)
    private val windows = linkedMapOf<Key,ArrayDeque<Sample>>()
    fun now() = nanoTime()
    fun recordSince(stage: PerformanceStage, start: Long, outcome: PerformanceOutcome = PerformanceOutcome.COMPLETE,
        screen: PerformanceScreen? = null) = recordMillis(stage,(nanoTime() - start).coerceAtLeast(0) / 1_000_000,outcome,screen)
    @Synchronized fun recordMillis(stage: PerformanceStage, ms: Long, outcome: PerformanceOutcome = PerformanceOutcome.COMPLETE,
        screen: PerformanceScreen? = null) {
        require((stage == PerformanceStage.NAV_COMPOSE) == (screen != null))
        val values = windows.getOrPut(Key(stage,screen)) { ArrayDeque() }
        if (values.size == 32) values.removeFirst()
        values.addLast(Sample(ms.coerceIn(0,600_000),outcome,ms !in 0..600_000))
    }
    suspend fun <T> measure(stage: PerformanceStage, block: suspend () -> T): T {
        val start = nanoTime();var outcome = PerformanceOutcome.FAILED
        return try { block().also { outcome = PerformanceOutcome.COMPLETE } }
        catch (error: CancellationException) { outcome = PerformanceOutcome.CANCELLED;throw error }
        finally { recordSince(stage,start,outcome) }
    }
    @Synchronized fun snapshot(): List<PerformanceSummary> = windows.entries.sortedWith(
        compareBy({ it.key.stage.ordinal },{ it.key.screen?.ordinal ?: -1 })).map { (key,window) ->
        val ok = window.filter { it.outcome == PerformanceOutcome.COMPLETE }.map { it.ms }.sorted()
        fun percentile(p: Int) = if (ok.isEmpty()) null else ok[(ok.size * p + 99) / 100 - 1]
        PerformanceSummary(key.stage,key.screen,window.size,ok.size,window.count { it.outcome == PerformanceOutcome.FAILED },
            window.count { it.outcome == PerformanceOutcome.CANCELLED },window.last().ms,percentile(50),percentile(95),
            ok.lastOrNull(),window.count { it.clamped })
    }
}

internal object PerformanceJournal {
    val recorder = PerformanceRecorder()
    fun snapshot() = recorder.snapshot()
    suspend fun <T> measure(stage: PerformanceStage, block: suspend () -> T) = recorder.measure(stage,block)
}

/** One pending span per owner. Old frame/disposal callbacks cannot finish a newer span. */
internal class PerformanceSpans(private val recorder: PerformanceRecorder) {
    private data class Pending(val ticket: Long, val stage: PerformanceStage, val screen: PerformanceScreen?, val start: Long)
    private var serial = 0L
    private var pending: Pending? = null
    @Synchronized fun currentTicket() = pending?.ticket
    @Synchronized fun start(stage: PerformanceStage, screen: PerformanceScreen? = null): Long {
        require((stage == PerformanceStage.NAV_COMPOSE) == (screen != null))
        pending?.let { recorder.recordSince(it.stage,it.start,PerformanceOutcome.CANCELLED,it.screen) }
        val ticket = ++serial;pending = Pending(ticket,stage,screen,recorder.now());return ticket
    }
    @Synchronized fun finish(ticket: Long?, outcome: PerformanceOutcome = PerformanceOutcome.COMPLETE): Boolean {
        val value = pending?.takeIf { it.ticket == ticket } ?: return false
        pending = null;recorder.recordSince(value.stage,value.start,outcome,value.screen);return true
    }
}