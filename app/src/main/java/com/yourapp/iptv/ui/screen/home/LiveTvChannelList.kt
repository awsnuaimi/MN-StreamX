package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.presentation.*
import kotlinx.coroutines.flow.*

@Composable
internal fun LiveTvChannelList(rows: List<LiveChannelRow>, selectedChannelId: String?, activeKey: String?,
    restore: HomeGridRestore?, labels: HomeLabels, colors: HomeTvPalette,
    program: (Channel) -> ChannelProgramInfo, badge: (Channel) -> String?, onFocused: (Channel) -> Unit,
    onWatch: (Channel) -> Unit, onMenu: (LiveChannelRow) -> Unit, onVisible: (List<Channel>) -> Unit,
    onPosition: (String, HomeGridPosition) -> Unit, onRestored: (Long) -> Unit,
    categoryFocus: FocusRequester, watchFocus: FocusRequester, rowFocus: FocusRequester,
    showDetail: Boolean, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var focusedId by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var focusKey by remember { mutableStateOf<String?>(null) }
    var restoredId by remember { mutableStateOf<String?>(null) }
    val restoreFocus = remember { FocusRequester() }
    val currentRows by rememberUpdatedState(rows)
    val visibleCallback by rememberUpdatedState(onVisible)
    val positionCallback by rememberUpdatedState(onPosition)
    val restoredCallback by rememberUpdatedState(onRestored)
    val preferred by rememberUpdatedState(selectedChannelId)
    val selectedIndex = rows.indexOfFirst { row -> row.variants.any { it.id == selectedChannelId } }
    LaunchedEffect(selectedChannelId, restore?.token, rows) {
        if (selectedIndex < 0 || (restoredId == selectedChannelId && restore == null)) return@LaunchedEffect
        val anchor = restore?.position?.anchorId?.let { id -> rows.indexOfFirst { row -> row.variants.any { it.id == id } } } ?: -1
        val target = if (anchor in 0..selectedIndex && selectedIndex - anchor < 8) anchor else selectedIndex
        restoring = true
        try {
            focusKey = rows[selectedIndex].key
            list.scrollToItem(target, if (target == anchor && restore?.position?.columns == 1)
                restore.position.offsetPx.coerceIn(0, (44 * density.density).toInt()) else 0)
            snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex } }.first { it }
            withFrameNanos { }; restoreFocus.requestFocus()
            restoredId = selectedChannelId
            restore?.let { restoredCallback(it.token) }
        } finally { restoring = false }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.filter {
            it.offset < list.layoutInfo.viewportEndOffset && it.offset + it.size > list.layoutInfo.viewportStartOffset
        }.mapNotNull { currentRows.getOrNull(it.index)?.channel(preferred) } }
            .distinctUntilChanged().collect { visibleCallback(it) }
    }
    DisposableEffect(list) { onDispose { visibleCallback(emptyList()) } }
    LaunchedEffect(list) {
        snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset,
            focusedId?.takeUnless { restoring || list.isScrollInProgress }) }
            .filter { it.third != null }.distinctUntilChanged().collect { (index, offset, id) ->
                val current = currentRows
                if (current.none { row -> row.variants.any { it.id == id } }) return@collect
                val anchor = current.getOrNull(index)?.channel(preferred)?.id ?: return@collect
                positionCallback(id!!, HomeGridPosition(anchor, offset, 1))
            }
    }
    BoxWithConstraints(modifier.focusGroup()) {
        val narrow = maxWidth < 430.dp || density.fontScale > 1.4f
        val rowHeight = (44f * density.fontScale.coerceAtLeast(1f)).dp
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("live-channel-list"),
            verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(vertical = 1.dp)) {
            itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, _ -> "live-reference-row" }) { index, row ->
                val channel = row.channel(selectedChannelId)
                LiveTvChannelRow(row, program(channel), badge(channel), row.key == activeKey, colors, labels,
                    { onWatch(channel) }, { onMenu(row) }, Modifier.height(rowHeight)
                        .testTag("live-channel-${row.key}")
                        .then(if (row.key == activeKey) Modifier.focusRequester(rowFocus) else Modifier)
                        .then(if (row.key == focusKey) Modifier.focusRequester(restoreFocus) else Modifier)
                        .focusProperties {
                            if (index == 0) up = categoryFocus
                            if (showDetail) { if (direction == LayoutDirection.Ltr) right = watchFocus else left = watchFocus }
                        }.onFocusChanged { if (it.isFocused) {
                            focusedId = channel.id; onFocused(channel)
                        } }, narrow)
            }
        }
    }
}