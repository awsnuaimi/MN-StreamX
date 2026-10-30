package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.input.key.KeyEvent
import com.yourapp.iptv.domain.model.HomeGridPosition
import com.yourapp.iptv.domain.performance.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One latest intent owns scrolling and focus; an outgoing screen cannot finish an old request. */
internal class LiveTvListNavigation(private val spans: PerformanceSpans = PerformanceSpans(PerformanceJournal.recorder)) {
    data class Request(val revision: Long, val key: String?, val position: HomeGridPosition? = null,
        val token: Long? = null, val instant: Boolean = false)
    private var revision = 0L
    private var measurement: Long? = null
    private val mutable = MutableStateFlow<Request?>(null)
    val requests = mutable.asStateFlow()
    var ownsFocus = false
    private var inputOwner: Any? = null
    private var input: (KeyEvent) -> Boolean = { false }
    fun attach(owner: Any, handler: (KeyEvent) -> Boolean) { inputOwner = owner; input = handler }
    fun detach(owner: Any) { if (inputOwner === owner) { inputOwner = null; input = { false }; cancel() } }
    fun handle(event: KeyEvent) = input(event)
    fun enter() { if (mutable.value == null) request(null) }
    fun request(key: String?, position: HomeGridPosition? = null, token: Long? = null, instant: Boolean = false) {
        measurement = spans.start(PerformanceStage.LIVE_FOCUS)
        mutable.value = Request(++revision, key, position, token, instant)
    }
    fun current(ticket: Long) = mutable.value?.revision == ticket
    fun finish(ticket: Long, outcome: PerformanceOutcome = PerformanceOutcome.COMPLETE) {
        if (current(ticket)) { spans.finish(measurement, outcome); measurement = null; mutable.value = null }
    }
    fun cancel() { mutable.value?.let { finish(it.revision, PerformanceOutcome.CANCELLED) } }
}

internal object LiveTvNavigationPolicy {
    fun visibleRows(viewportPx: Int, rowPx: Int, spacingPx: Int): Int =
        ((viewportPx.coerceAtLeast(0).toLong() + spacingPx.coerceAtLeast(0)) /
            (rowPx.coerceAtLeast(1).toLong() + spacingPx.coerceAtLeast(0))).toInt().coerceAtLeast(1)
    fun target(index: Int, delta: Int, count: Int): Int =
        if (count <= 0) -1 else (index.toLong() + delta.toLong()).coerceIn(0, count.toLong() - 1).toInt()
}