package com.yourapp.iptv.ui.screen.home

import androidx.compose.ui.input.key.KeyEvent
import android.view.KeyEvent as AndroidKey
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
    val upShortcut = LiveTvUpShortcut()
    private var context: Any? = null
    private var upOrigin: Request? = null
    private var categoryReturn: Request? = null
    private var categoryTransfer = false
    private var categoryFocused = false
    private var selectActiveCategory = false
    private var inputOwner: Any? = null
    private var input: (KeyEvent) -> Boolean = { false }
    fun attach(owner: Any, handler: (KeyEvent) -> Boolean) { inputOwner = owner; input = handler }
    fun detach(owner: Any) { if (inputOwner === owner) { inputOwner = null; input = { false }; cancel(); clearShortcut() } }
    fun handle(event: KeyEvent) = input(event)
    fun enter() {
        categoryTransfer = false; clearShortcut()
        if (mutable.value == null) {
            val saved = categoryReturn; categoryReturn = null
            request(saved?.key, saved?.position, instant = saved != null)
        }
    }
    fun bind(value: Any) {
        if (context == value) return
        context = value; cancel(); clearShortcut(); categoryReturn = null; selectActiveCategory = false
    }
    fun origin(key: String, position: HomeGridPosition) { upOrigin = Request(0, key, position) }
    fun categories(): Request? {
        cancel(); categoryReturn = upOrigin; upOrigin = null
        categoryTransfer = true; selectActiveCategory = true
        return categoryReturn
    }
    fun takeCategorySelection(): Boolean = selectActiveCategory.also { selectActiveCategory = false }
    fun clearShortcut() { upShortcut.reset(); upOrigin = null; categoryTransfer = false }
    fun leave() { ownsFocus = false; cancel(); if (!categoryTransfer) clearShortcut() }
    fun focused() { categoryReturn = null }
    fun categoryFocus(value: Boolean) { categoryFocused = value }
    fun leftCategories() { if (!categoryFocused && categoryTransfer && !ownsFocus) clearShortcut() }
    fun categoryHandoff(event: KeyEvent): Boolean = categoryTransfer && categoryFocused && categoryKey(event)
    fun categoryKey(event: KeyEvent): Boolean {
        val key = event.nativeKeyEvent
        if (key.isCanceled || key.isAltPressed || key.isCtrlPressed || key.isMetaPressed || key.isShiftPressed) {
            clearShortcut(); return false
        }
        if (key.keyCode != AndroidKey.KEYCODE_DPAD_UP) {
            if (key.action == AndroidKey.ACTION_DOWN) clearShortcut()
            return false
        }
        if (key.action == AndroidKey.ACTION_UP) upShortcut.up(key.eventTime, key.isCanceled, key.deviceId)
        if (key.action != AndroidKey.ACTION_DOWN) return false
        return upShortcut.down(key.eventTime, key.repeatCount > 0 || key.isLongPress,
            key.deviceId, canStart = false) == LiveTvUpShortcut.Result.DOUBLE
    }
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