package com.yourapp.iptv.ui.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.ui.presentation.ChannelProgramInfo
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeChannelLayout
import com.yourapp.iptv.domain.model.HomeGridPosition
import com.yourapp.iptv.domain.model.HomeGridRestore
import com.yourapp.iptv.ui.component.channel.ChannelCard
import com.yourapp.iptv.ui.presentation.TvGridLayout
import com.yourapp.iptv.ui.presentation.HomeGridRestorePolicy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** One lazy grid also implements the one-column list: stable item identity in both modes. */
@Composable
fun TvChannelGrid(channels: List<Channel>, selectedChannelId: String?, onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier, columns: Int? = null, showGroup: Boolean = true,
    restore: HomeGridRestore? = null,
    onBrowsePosition: (String, HomeGridPosition) -> Unit = { _, _ -> },
    onRestoreHandled: (Long) -> Unit = {},
    layout: HomeChannelLayout = HomeChannelLayout.GRID, showLogos: Boolean = true,
    showGuide: Boolean = false, language: HomeLanguage = HomeLanguage.ARABIC,
    program: (Channel) -> ChannelProgramInfo = { ChannelProgramInfo() },
    onVisibleChannels: (List<Channel>) -> Unit = {}) {
    val grid = rememberLazyGridState()
    val restoreFocus = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var gridHasFocus by remember { mutableStateOf(false) }
    var restoredId by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var focusTargetId by remember { mutableStateOf<String?>(null) }
    val currentChannels by rememberUpdatedState(channels)
    val reportPosition by rememberUpdatedState(onBrowsePosition)
    val reportHandled by rememberUpdatedState(onRestoreHandled)
    val reportVisible by rememberUpdatedState(onVisibleChannels)
    LaunchedEffect(grid, showGuide) {
        if (showGuide) snapshotFlow {
            val info = grid.layoutInfo
            info.visibleItemsInfo.asSequence()
                .filter { it.offset.y < info.viewportEndOffset && it.offset.y + it.size.height > info.viewportStartOffset }
                .sortedBy { it.index }.mapNotNull { currentChannels.getOrNull(it.index) }.toList()
        }.distinctUntilChanged().collect { reportVisible(it) }
    }
    DisposableEffect(grid, showGuide) { onDispose { if (showGuide) reportVisible(emptyList()) } }
    val selectedIndex = remember(channels, selectedChannelId) { channels.indexOfFirst { it.id == selectedChannelId } }
    BoxWithConstraints(modifier.onFocusChanged { gridHasFocus = it.hasFocus }.focusGroup()) {
        val density = LocalDensity.current
        val columnCount = TvGridLayout.channelColumns(maxWidth.value, columns ?: 4, density.fontScale, layout)
        val rows = TvGridLayout.channelRows(maxHeight.value, density.fontScale, layout, showGuide)
        val dimensions = GridDimensions(columnCount, rows.count, rows.height)
        var previousDimensions by remember { mutableStateOf(dimensions) }
        LaunchedEffect(selectedChannelId, selectedIndex, restore?.token, dimensions) {
            val reflow = previousDimensions != dimensions
            val oldColumns = previousDimensions.columns
            previousDimensions = dimensions
            if (selectedChannelId == null) restoredId = null
            val restoreSelection = selectedIndex >= 0 && (restore != null || restoredId != selectedChannelId)
            if (!restoreSelection && !reflow) return@LaunchedEffect
            val focused = focusedId?.let { id -> channels.indexOfFirst { it.id == id } } ?: -1
            val targetIndex = if (restoreSelection) selectedIndex
                else focused.takeIf { it >= 0 } ?: grid.firstVisibleItemIndex.coerceAtMost(channels.lastIndex)
            if (targetIndex < 0) return@LaunchedEffect
            val takeFocus = restoreSelection || gridHasFocus
            val position = if (restoreSelection) restore?.position else null
            val anchor = if (restoreSelection) position?.anchorId?.let { id -> channels.indexOfFirst { it.id == id } } ?: -1
                else grid.firstVisibleItemIndex
            val target = HomeGridRestorePolicy.target(anchor, targetIndex, columnCount, rows.count,
                (position?.offsetPx ?: 0).coerceAtMost(((rows.height + 8f) * density.density).toInt().coerceAtLeast(1) - 1),
                position?.columns ?: if (reflow) 0 else oldColumns)
            restoring = true
            try {
                if (takeFocus) focusTargetId = channels[targetIndex].id
                grid.scrollToItem(target.index, target.offset)
                snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }.first { it }
                if (takeFocus) { withFrameNanos { }; restoreFocus.requestFocus() }
                if (restoreSelection) {
                    restoredId = selectedChannelId
                    restore?.let { reportHandled(it.token) }
                }
            } finally { restoring = false }
        }
        LaunchedEffect(grid, dimensions) {
            snapshotFlow { GridObservation(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset,
                focusedId, grid.isScrollInProgress || restoring) }
                .filter { !it.busy && it.focused != null }
                .distinctUntilChanged()
                .collect { value ->
                    val current = currentChannels
                    val id = value.focused ?: return@collect
                    // An outgoing focus event must not be attached to a replaced list.
                    if (current.getOrNull(focusedIndex)?.id != id) return@collect
                    val anchor = current.getOrNull(value.first)?.id ?: return@collect
                    reportPosition(id, HomeGridPosition(anchor, value.offset, columnCount))
                }
        }
        // Focus bring-into-view owns normal scrolling. No second idle-time snap.
        LazyVerticalGrid(columns = GridCells.Fixed(columnCount), state = grid,
            modifier = Modifier.fillMaxSize().testTag("channel-browser"),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(channels, key = { _, channel -> channel.id }, contentType = { _, _ -> "channel" }) { index, channel ->
                ChannelCard(channel, { onChannelClick(channel) }, Modifier.height(rows.height.dp)
                    .testTag("browse-channel-${channel.id}")
                    .onFocusChanged { if (it.isFocused) { focusedIndex = index; focusedId = channel.id } }
                    .then(if (channel.id == focusTargetId) Modifier.focusRequester(restoreFocus) else Modifier),
                    showGroup, showLogos, compact = true, listMode = layout == HomeChannelLayout.LIST,
                    program = if (showGuide) program(channel) else null, language = language)
            }
        }
    }
}

private data class GridDimensions(val columns: Int, val rows: Int, val height: Float)
private data class GridObservation(val first: Int, val offset: Int, val focused: String?, val busy: Boolean)