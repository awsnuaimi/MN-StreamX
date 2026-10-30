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
import com.yourapp.iptv.domain.performance.PerformanceOutcome
import kotlinx.coroutines.CancellationException
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
    sidebarFocus: FocusRequester? = null, ready: Boolean = true, preferredQualities: Map<String, String> = emptyMap(),
    index: LiveChannelIndex = remember(rows) { LiveChannelIndex(rows) },
    sidebarOnRight: Boolean? = null, emptyEntry: FocusRequester? = null,
    onEnterDetails: (() -> Unit)? = null) {
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val sidebarRight = sidebarOnRight ?: (direction == LayoutDirection.Rtl)
    val rowHeight = (44f * density.fontScale.coerceAtLeast(1f)).dp
    val rowPx = with(density) { rowHeight.roundToPx() }.coerceAtLeast(1)
    val spacingPx = with(density) { 5.dp.roundToPx() }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var restoredId by remember { mutableStateOf<String?>(null) }
    var geometry by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val mounted = remember { mutableStateMapOf<String, FocusRequester>() }
    val currentRows by rememberUpdatedState(rows)
    val currentIndex by rememberUpdatedState(index)
    val currentActive by rememberUpdatedState(activeKey)
    val visibleCallback by rememberUpdatedState(onVisible)
    val positionCallback by rememberUpdatedState(onPosition)
    val restoredCallback by rememberUpdatedState(onRestored)
    val preferred by rememberUpdatedState(selectedChannelId)
    val defaults by rememberUpdatedState(preferredQualities)
    val viewport by remember { derivedStateOf { list.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset } } }
    val selectedIndex = index.channelPosition(selectedChannelId)

    LaunchedEffect(selectedChannelId, restore?.token, rows, viewport, rowPx, navigation.ownsFocus) {
        if (selectedChannelId == null) restoredId = null
        if (!ready || rows.isEmpty() || viewport <= 0) return@LaunchedEffect
        val dimensions = viewport to rowPx
        val reflow = geometry != null && geometry != dimensions
        geometry = dimensions
        val selected = selectedIndex >= 0 && (restoredId != selectedChannelId || restore != null)
        if (selected && restore?.takeFocus == false && !navigation.ownsFocus) return@LaunchedEffect
        if (!selected && (!reflow || !navigation.ownsFocus)) return@LaunchedEffect
        val selectedTarget = if (selected) selectedIndex else index.keyPosition(activeKey).coerceAtLeast(0)
        val position = if (selected) restore?.position else HomeGridPosition(
            rows.getOrNull(list.firstVisibleItemIndex)?.channel(selectedChannelId, preferredQualities)?.id, list.firstVisibleItemScrollOffset, 1)
        navigation.request(rows[selectedTarget].key, position, if (selected) restore?.token else null, instant = true)
    }
    LaunchedEffect(navigation, rows, ready, rowPx) {
        navigation.requests.collectLatest { request ->
            if (request == null || !ready || rows.isEmpty()) return@collectLatest
            val targetIndex = index.keyPosition(request.key ?: currentActive).takeIf { it >= 0 }
                ?: index.keyPosition(currentActive).takeIf { it >= 0 } ?: 0
            restoring = true
            try {
                snapshotFlow { list.layoutInfo.totalItemsCount == rows.size && viewport > 0 }.first { it }
                if (request.instant) {
                    val anchor = request.position?.anchorId?.let { id -> index.channelPosition(id) } ?: -1
                    val target = HomeGridRestorePolicy.target(anchor, targetIndex, 1,
                        LiveTvNavigationPolicy.visibleRows(viewport, rowPx, spacingPx),
                        request.position?.offsetPx?.coerceIn(0, rowPx + spacingPx - 1) ?: 0,
                        request.position?.columns ?: 0)
                    list.scrollToItem(target.index, target.offset)
                } else {
                    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                    if (item == null) list.animateScrollToItem(targetIndex) else {
                        val info = list.layoutInfo
                        val delta = when {
                            item.offset < info.viewportStartOffset -> item.offset - info.viewportStartOffset
                            item.offset + item.size > info.viewportEndOffset -> item.offset + item.size - info.viewportEndOffset
                            else -> 0
                        }
                        if (delta != 0) list.animateScrollBy(delta.toFloat())
                    }
                }
                snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } && mounted[rows[targetIndex].key] != null }.first { it }
                if (!navigation.current(request.revision)) return@collectLatest
                mounted[rows[targetIndex].key]?.requestFocus()
                snapshotFlow { focusedKey == rows[targetIndex].key }.first { it }
                if (!navigation.current(request.revision)) return@collectLatest
                if (rows[targetIndex].variants.any { it.id == preferred }) restoredId = preferred
                request.token?.let(restoredCallback)
                navigation.finish(request.revision)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                navigation.finish(request.revision, PerformanceOutcome.FAILED); throw error
            } finally { restoring = false }
        }
    }
    val handler by rememberUpdatedState<(KeyEvent) -> Boolean>({ event ->
        val key = event.nativeKeyEvent
        if (key.isCanceled) navigation.clearShortcut()
        if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyUp) {
            navigation.upShortcut.up(key.eventTime, key.isCanceled, key.deviceId)
        } else if (event.type == KeyEventType.KeyDown && event.key != Key.DirectionUp) navigation.clearShortcut()
        val delta = when (event.key) {
            Key.DirectionUp -> -1; Key.DirectionDown -> 1
            Key.PageUp, Key.ChannelUp -> -LiveTvNavigationPolicy.visibleRows(viewport, rowPx, spacingPx)
            Key.PageDown, Key.ChannelDown -> LiveTvNavigationPolicy.visibleRows(viewport, rowPx, spacingPx)
            else -> 0
        }
        val horizontal = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
        if (delta == 0 && !horizontal) false
        else if (event.type == KeyEventType.KeyUp) true
        else if (event.type != KeyEventType.KeyDown) false
        else if (event.nativeKeyEvent.isCanceled) true
        else if (!ready || rows.isEmpty()) {
            when {
                event.key == Key.DirectionUp -> { navigation.cancel(); categoryFocus.requestFocus() }
                event.key == Key.DirectionDown && emptyEntry != null -> emptyEntry.requestFocus()
                horizontal && HomeDpadPolicy.towardSidebar(homeDirection(event.key)!!, sidebarRight) -> {
                    navigation.cancel(); sidebarFocus?.requestFocus()
                }
            }
            true
        } else {
            val pending = navigation.requests.value?.key
            val position = index.keyPosition(pending ?: activeKey).coerceAtLeast(0)
            val gesture = if (event.key == Key.DirectionUp && !key.isAltPressed && !key.isCtrlPressed && !key.isMetaPressed && !key.isShiftPressed)
                navigation.upShortcut.down(key.eventTime, key.repeatCount > 0 || key.isLongPress, key.deviceId)
                else LiveTvUpShortcut.Result.NORMAL
            if (event.key == Key.DirectionUp && gesture == LiveTvUpShortcut.Result.NORMAL) navigation.clearShortcut()
            if (gesture == LiveTvUpShortcut.Result.START) {
                val origin = index.keyPosition(focusedKey ?: activeKey).takeIf { it >= 0 } ?: position
                val anchor = rows.getOrNull(list.firstVisibleItemIndex)?.channel(preferred, defaults)?.id
                navigation.origin(rows[origin].key, HomeGridPosition(anchor, list.firstVisibleItemScrollOffset, 1))
            }
            fun categories() {
                val saved = navigation.categories()
                val savedPosition = saved?.position
                val origin = saved?.key?.let(index::keyPosition)?.takeIf { it >= 0 }
                if (origin != null && savedPosition != null) positionCallback(rows[origin].channel(preferred, defaults).id, savedPosition)
                categoryFocus.requestFocus()
            }
            if (gesture == LiveTvUpShortcut.Result.DOUBLE) {
                categories()
            } else if (horizontal) {
                val details = if (sidebarRight) Key.DirectionLeft else Key.DirectionRight
                navigation.cancel()
                if (event.key == details && showDetail) { if (onEnterDetails != null) onEnterDetails() else watchFocus.requestFocus() }
                else if (event.key != details && sidebarFocus != null) sidebarFocus.requestFocus()
            } else if (event.key == Key.DirectionUp && position == 0) {
                categories()
            } else {
                val next = LiveTvNavigationPolicy.target(position, delta, rows.size)
                if (next != position) navigation.request(rows[next].key)
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
                if (currentIndex.channelPosition(id) < 0) return@collect
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
                        .onFocusChanged {
                            if (it.isFocused) { navigation.focused(); focusedKey = row.key; focusedId = channel.id; onFocused(channel) }
                            else if (focusedKey == row.key) focusedKey = null
                        },
                    narrow, showLogos = showLogos, preferredChannelId = channel.id)
            }
        }
    }
}