package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.yourapp.iptv.ui.component.common.restoreOrRequestFocus

internal data class LiveTvCategoryTarget(val key: String, val tag: String, val title: String,
    val selected: Boolean, val onClick: () -> Unit)

/** The entry is always mounted; a lazy chip receives focus only after it is composed. */
@Composable
internal fun LiveTvCategoryFocus(items: List<LiveTvCategoryTarget>, activeIndex: Int,
    colors: HomeTvPalette, entry: FocusRequester, header: FocusRequester?, listEntry: FocusRequester?,
    sidebar: FocusRequester?, sidebarOnRight: Boolean, headerFallback: FocusRequester? = null) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mounted = remember { mutableStateMapOf<String, FocusRequester>() }
    var inside by remember { mutableStateOf(false) }
    var lastKey by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var revision by remember { mutableLongStateOf(0) }
    val current by rememberUpdatedState(items)
    val keys = items.map { it.key }
    fun cancel() { revision++; job?.cancel(); job = null; pending = null }
    fun focus(index: Int) {
        val key = items.getOrNull(index)?.key ?: return
        cancel(); pending = key
        val ticket = revision
        job = scope.launch {
            snapshotFlow { list.layoutInfo.totalItemsCount == current.size }.first { it }
            list.scrollToItem(index)
            snapshotFlow { mounted[key] }.first { it != null }
            if (inside && revision == ticket && current.getOrNull(index)?.key == key) {
                mounted[key]?.requestFocus()
                pending = null
            }
        }
    }
    LaunchedEffect(keys, activeIndex) {
        if (inside) focus(activeIndex)
        else if (items.isNotEmpty()) list.scrollToItem(activeIndex.coerceIn(items.indices))
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    Box(Modifier.fillMaxWidth().focusRequester(entry).onFocusChanged {
        inside = it.hasFocus
        if (it.isFocused) focus(items.indexOfFirst { item -> item.key == lastKey }
            .takeIf { index -> index >= 0 } ?: activeIndex)
        else if (!it.hasFocus) cancel()
    }.homeDpadKeys { direction ->
        val index = items.indexOfFirst { it.key == (pending ?: lastKey) }.takeIf { it >= 0 } ?: activeIndex
        when (direction) {
            HomeDpad.UP -> { cancel(); header?.restoreOrRequestFocus(headerFallback) }
            HomeDpad.DOWN -> { cancel(); listEntry?.requestFocus() }
            else -> {
                val next = HomeDpadPolicy.neighbor(index, items.size, direction, false, rtl)
                if (next != null) focus(next)
                else if (HomeDpadPolicy.towardSidebar(direction, sidebarOnRight) && sidebar != null) {
                    cancel(); sidebar.requestFocus()
                }
            }
        }
    }.focusable()) {
        LazyRow(state = list, modifier = Modifier.fillMaxWidth().testTag("live-categories"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(items, key = { it.key }) { item ->
                val requester = remember(item.key) { FocusRequester() }
                DisposableEffect(item.key, requester) {
                    mounted[item.key] = requester
                    onDispose { if (mounted[item.key] === requester) mounted.remove(item.key) }
                }
                LiveTvPill(item.title, colors, item.onClick, Modifier.focusRequester(requester)
                    .onFocusChanged { if (it.isFocused) lastKey = item.key }
                    .testTag(item.tag).semantics { selected = item.selected }, item.selected)
            }
        }
    }
}