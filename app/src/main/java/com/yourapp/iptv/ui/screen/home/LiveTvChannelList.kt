package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
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
    categoryFocus: FocusRequester, watchFocus: FocusRequester,
    showDetail: Boolean, modifier: Modifier = Modifier, showLogos: Boolean = true,
    navigation: LiveTvListNavigation = remember { LiveTvListNavigation() },
    sidebarFocus: FocusRequester? = null, ready: Boolean = true, preferredQualities: Map<String, String> = emptyMap()) {
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val rowHeight = (44f * density.fontScale.coerceAtLeast(1f)).dp
    val rowPx = with(density) { rowHeight.roundToPx() }.coerceAtLeast(1)
    val spacingPx = with(density) { 5.dp.roundToPx() }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var restoredId by remember { mutableStateOf<String?>(null) }
    var geometry by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val mounted = remember { mutableStateMapOf<String, FocusRequester>() }
    val currentRows by rememberUpdatedState(rows)
    val currentActive by rememberUpdatedState(activeKey)
    val visibleCallback by rememberUpdatedState(onVisible)
    val positionCallback by rememberUpdatedState(onPosition)
    val restoredCallback by rememberUpdatedState(onRestored)
    val preferred by rememberUpdatedState(selectedChannelId)
    val defaults by rememberUpdatedState(preferredQualities)
    val viewport by remember { derivedStateOf { list.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset } } }
    val selectedIndex = rows.indexOfFirst { row -> row.variants.any { it.id == selectedChannelId } }

    LaunchedEffect(selectedChannelId, restore?.token, rows, viewport, rowPx) {
        if (selectedChannelId == null) restoredId = null
        if (!ready || rows.isEmpty() || viewport <= 0) return@LaunchedEffect
        val dimensions = viewport to rowPx
        val reflow = geometry != null && geometry != dimensions
        geometry = dimensions
        val selected = selectedIndex >= 0 && (restoredId != selectedChannelId || restore != null)
        if (!selected && (!reflow || !navigation.ownsFocus)) return@LaunchedEffect
        val index = if (selected) selectedIndex else rows.indexOfFirst { it.key == activeKey }.coerceAtLeast(0)
        val position = if (selected) restore?.position else HomeGridPosition(
            rows.getOrNull(list.firstVisibleItemIndex)?.channel(selectedChannelId, preferredQualities)?.id, list.firstVisibleItemScrollOffset, 1)
        navigation.request(rows[index].key, position, if (selected) restore?.token else null, instant = true)
    }
    LaunchedEffect(navigation, rows, ready, rowPx) {
        navigation.requests.collectLatest { request ->
            if (request == null || !ready || rows.isEmpty()) return@collectLatest
            val index = rows.indexOfFirst { it.key == (request.key ?: currentActive) }.takeIf { it >= 0 }
                ?: rows.indexOfFirst { it.key == currentActive }.takeIf { it >= 0 } ?: 0
            restoring = true
            try {
                snapshotFlow { list.layoutInfo.totalItemsCount == rows.size && viewport > 0 }.first { it }
                if (request.instant) {
                    val anchor = request.position?.anchorId?.let { id -> rows.indexOfFirst { row -> row.variants.any { it.id == id } } } ?: -1
                    val target = HomeGridRestorePolicy.target(anchor, index, 1,
                        LiveTvNavigationPolicy.visibleRows(viewport, rowPx, spacingPx),
                        request.position?.offsetPx?.coerceIn(0, rowPx + spacingPx - 1) ?: 0,
                        request.position?.columns ?: 0)
                    list.scrollToItem(target.index, target.offset)
                } else {
                    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    if (item == null) list.animateScrollToItem(index) else {
                        val info = list.layoutInfo
                        val delta = when {
                            item.offset < info.viewportStartOffset -> item.offset - info.viewportStartOffset
                            item.offset + item.size > info.viewportEndOffset -> item.offset + item.size - info.viewportEndOffset
                            else -> 0
                        }
                        if (delta != 0) list.animateScrollBy(delta.toFloat())
                    }
                }
                snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.index == index } && mounted[rows[index].key] != null }.first { it }
                if (!navigation.current(request.revision)) return@collectLatest
                mounted[rows[index].key]?.requestFocus()
                if (rows[index].variants.any { it.id == preferred }) restoredId = preferred
                request.token?.let(restoredCallback)
                navigation.finish(request.revision)
            } finally { restoring = false }
        }
    }
    val handler by rememberUpdatedState<(KeyEvent) -> Boolean>({ event ->
        val delta = when (event.key) {
            Key.DirectionUp -> -1; Key.DirectionDown -> 1
            Key.PageUp, Key.ChannelUp -> -LiveTvNavigationPolicy.visibleRows(viewport, rowPx, spacingPx)
            Key.PageDown, Key.ChannelDown -> LiveTvNavigationPolicy.visibleRows(viewport, rowPx, spacingPx)
            else -> 0
        }
        val horizontal = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
        if (!ready || rows.isEmpty() || (delta == 0 && !horizontal)) false
        else if (event.type == KeyEventType.KeyUp) true
        else if (event.type != KeyEventType.KeyDown) false
        else {
            val pending = navigation.requests.value?.key
            val index = rows.indexOfFirst { it.key == (pending ?: activeKey) }.coerceAtLeast(0)
            if (horizontal) {
                val details = if (direction == LayoutDirection.Ltr) Key.DirectionRight else Key.DirectionLeft
                navigation.cancel()
                if (event.key == details && showDetail) watchFocus.requestFocus()
                else if (event.key != details && sidebarFocus != null) sidebarFocus.requestFocus()
            } else if (event.key == Key.DirectionUp && index == 0) {
                navigation.cancel(); categoryFocus.requestFocus()
            } else {
                val next = LiveTvNavigationPolicy.target(index, delta, rows.size)
                if (next != index) navigation.request(rows[next].key)
            }
            true
        }
    })
    DisposableEffect(navigation) {
        val owner = Any(); navigation.attach(owner) { handler(it) }
        onDispose { navigation.detach(owner) }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.filter {
            it.offset < list.layoutInfo.viewportEndOffset && it.offset + it.size > list.layoutInfo.viewportStartOffset
        }.mapNotNull { currentRows.getOrNull(it.index)?.channel(preferred, defaults) } }
            .distinctUntilChanged().collect { visibleCallback(it) }
    }
    DisposableEffect(list) { onDispose { visibleCallback(emptyList()) } }
    LaunchedEffect(list) {
        snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset,
            focusedId?.takeUnless { restoring || list.isScrollInProgress }) }
            .filter { it.third != null }.distinctUntilChanged().collect { (index, offset, id) ->
                if (currentRows.none { row -> row.variants.any { it.id == id } }) return@collect
                val anchor = currentRows.getOrNull(index)?.channel(preferred, defaults)?.id ?: return@collect
                positionCallback(id!!, HomeGridPosition(anchor, offset, 1))
            }
    }
    BoxWithConstraints(modifier) {
        val narrow = maxWidth < 430.dp || density.fontScale > 1.4f
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()
            .testTag(if (ready) "live-channel-list" else "live-channel-list-loading"),
            verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(vertical = 1.dp)) {
            itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, _ -> "live-reference-row" }) { _, row ->
                val channel = row.channel(selectedChannelId, preferredQualities)
                val rowFocus = remember(row.key) { FocusRequester() }
                DisposableEffect(row.key, rowFocus) {
                    mounted[row.key] = rowFocus
                    onDispose { if (mounted[row.key] === rowFocus) mounted.remove(row.key) }
                }
                LiveTvChannelRow(row, program(channel), badge(channel), row.key == activeKey, colors, labels,
                    { onWatch(channel) }, { onMenu(row) }, Modifier.height(rowHeight)
                        .testTag("live-channel-${row.key}")
                        .focusRequester(rowFocus)
                        .onFocusChanged { if (it.isFocused) { focusedId = channel.id; onFocused(channel) } },
                    narrow, showLogos = showLogos, preferredChannelId = channel.id)
            }
        }
    }
}