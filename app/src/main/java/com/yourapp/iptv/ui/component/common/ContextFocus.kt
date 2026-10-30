@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.yourapp.iptv.ui.component.common

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** One UI owner, one pending intent. New physical input cancels all delayed restoration. */
internal class ContextFocus(val bookmarks: FocusBookmarks) {
    data class Request(val sequence: Long, val context: String, val preferred: String?,
        val place: FocusBookmarks.Place?, val inputEpoch: Long)
    private data class Node(val context: String, val key: String)
    private val nodes = mutableStateMapOf<Node, Pair<Int, FocusRequester>>()
    private var sequence = 0L
    var pending by mutableStateOf<Request?>(null)
        private set
    private var actualFocus by mutableStateOf<Node?>(null)
    private var applying: Node? = null

    fun cancel() { pending = null; applying = null }
    fun request(context: String, preferred: String? = null, place: FocusBookmarks.Place? = bookmarks.get(context)) {
        applying = null
        pending = Request(++sequence, context, preferred, place, FocusInputEpoch.value)
    }
    fun restore(contexts: Set<String>, fallback: String, preferred: String? = null) {
        val last = bookmarks.lastContext?.takeIf { it in contexts }
        request(last ?: fallback, if (last == null) preferred else null)
    }
    fun record(context: String, key: String, index: Int) {
        // Explicit activation supersedes an earlier intent, including touch/semantics actions.
        cancel()
        bookmarks.record(context, key, index)
    }
    fun registered(context: String): List<String> = nodes.entries.filter { it.key.context == context }
        .sortedWith(compareBy({ it.value.first }, { it.key.key })).map { it.key.key }
    fun dispose() { cancel(); nodes.clear(); actualFocus = null }

    @Composable
    fun target(context: String, key: String, index: Int): Modifier {
        val requester = remember(this, context, key) { FocusRequester() }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(this, context, key, requester) {
            val node = Node(context, key)
            nodes[node] = index to requester
            onDispose {
                if (nodes[node]?.second === requester) nodes.remove(node)
                if (actualFocus == node) actualFocus = null
            }
        }
        SideEffect { nodes[Node(context, key)] = index to requester }
        return Modifier.focusRequester(requester).onFocusChanged {
            val node = Node(context, key)
            if (it.isFocused) {
                actualFocus = node
                // Lazy removal can temporarily focus the first toolbar button. Physical input
                // already invalidates pending work; this passive handoff must not replace it.
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && (pending == null || applying == node))
                    bookmarks.record(context, key, index)
            } else if (actualFocus == node) actualFocus = null
        }
    }

    fun inputModifier(): Modifier = Modifier.onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown) { FocusInputEpoch.advance(); cancel() }
        false
    }.pointerInput(this) {
        awaitPointerEventScope {
            while (true) {
                if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) { FocusInputEpoch.advance(); cancel() }
            }
        }
    }

    @Composable
    fun Restore(context: String, keys: List<String>, ready: Boolean = true, fallbackKey: String? = null,
        onEmpty: () -> Unit = {}, scrollTo: suspend (Int) -> Unit = {}) {
        val request = pending?.takeIf { it.context == context }
        val inputEpoch = if (request != null) FocusInputEpoch.value else null
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val inputMode = LocalInputModeManager.current
        val currentEmpty by rememberUpdatedState(onEmpty)
        val currentScroll by rememberUpdatedState(scrollTo)
        LaunchedEffect(this, request, keys, ready, inputEpoch, fallbackKey) {
            if (request == null) return@LaunchedEffect
            if (request.inputEpoch != inputEpoch) { if (pending == request) cancel(); return@LaunchedEffect }
            if (!ready) return@LaunchedEffect
            // Navigation keeps outgoing destinations STARTED during transitions. Only the
            // resumed destination may take focus; disposal/input cancels this suspended intent.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            if (pending != request || request.inputEpoch != FocusInputEpoch.value) return@LaunchedEffect
            val index = FocusBookmarks.resolve(request.place, keys,
                request.preferred ?: fallbackKey.takeIf { request.place == null })
            if (index == null) {
                if (pending == request) { cancel(); currentEmpty() }
                return@LaunchedEffect
            }
            val key = keys[index]
            var restored = false
            withTimeoutOrNull(1500) {
                withFrameNanos { }
                if (pending != request || request.inputEpoch != FocusInputEpoch.value ||
                    !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@withTimeoutOrNull
                currentScroll(index)
                val requester = snapshotFlow { nodes[Node(context, key)]?.second }.first { it != null }
                withFrameNanos { }
                if (pending == request && request.inputEpoch == FocusInputEpoch.value &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    // A node may detach between composition and the next frame.
                    val target = Node(context, key)
                    applying = target
                    inputMode.requestInputMode(InputMode.Keyboard)
                    try {
                        requester?.requestFocus()
                        snapshotFlow { actualFocus == target }.first { it }
                        if (pending == request && request.inputEpoch == FocusInputEpoch.value) {
                            bookmarks.record(context, key, index)
                            restored = true
                        }
                    } catch (_: IllegalStateException) { }
                }
            }
            if (pending == request) {
                cancel()
                if (!restored && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) currentEmpty()
            }
        }
    }

    /** Request recovery only on a data transition while this region owned focus. */
    @Composable
    fun Maintain(context: String, keys: List<String>, ready: Boolean = true) {
        val previous = remember(this, context) { Availability() }
        val active = actualFocus
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        SideEffect {
            val removed = ready && previous.ready && previous.place?.key?.let { it !in keys } == true
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && previous.owned &&
                previous.inputEpoch == FocusInputEpoch.value && pending == null &&
                ((previous.ready && !ready) || removed)) {
                request(context, place = previous.place)
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && active?.context == context && pending == null) {
                keys.indexOf(active.key).takeIf { it >= 0 }?.let { bookmarks.record(context, active.key, it) }
            }
            previous.ready = ready
            previous.place = bookmarks.get(context)
            previous.owned = active?.context == context
            previous.inputEpoch = FocusInputEpoch.value
        }
    }

    private class Availability(var ready: Boolean = false, var place: FocusBookmarks.Place? = null,
        var owned: Boolean = false, var inputEpoch: Long = 0)
}

@Composable
internal fun rememberContextFocus(owner: String): ContextFocus {
    val saver = remember(owner) {
        listSaver<FocusBookmarks, Any>(
            save = { memory -> listOf(owner) + memory.entries().flatMap { (context, place) -> listOf(context, place.key, place.index) } },
            restore = { values -> FocusBookmarks().also { memory ->
                if (values.firstOrNull() == owner) values.drop(1).chunked(3).takeLast(FocusBookmarks.LIMIT).forEach {
                    if (it.size == 3 && it[0] is String && it[1] is String && it[2] is Int)
                        memory.record(it[0] as String, it[1] as String, it[2] as Int)
                }
            } })
    }
    val memory = rememberSaveable(owner, saver = saver) { FocusBookmarks() }
    val focus = remember(owner, memory) { ContextFocus(memory) }
    DisposableEffect(focus) { onDispose { focus.dispose() } }
    return focus
}