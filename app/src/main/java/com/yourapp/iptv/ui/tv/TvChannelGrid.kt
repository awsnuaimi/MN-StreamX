package com.yourapp.iptv.ui.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.HomeGridPosition
import com.yourapp.iptv.domain.model.HomeGridRestore
import com.yourapp.iptv.ui.component.channel.ChannelCard
import com.yourapp.iptv.ui.presentation.TvGridLayout
import com.yourapp.iptv.ui.presentation.HomeGridRestorePolicy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
fun TvChannelGrid(channels: List<Channel>, selectedChannelId: String?, onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier, columns: Int? = null, showGroup: Boolean = true,
    restore: HomeGridRestore? = null,
    onBrowsePosition: (String, HomeGridPosition) -> Unit = { _, _ -> },
    onRestoreHandled: (Long) -> Unit = {}) {
    val grid = rememberLazyGridState()
    val restoreFocus = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var gridHasFocus by remember { mutableStateOf(false) }
    var restoredId by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    val currentChannels by rememberUpdatedState(channels)
    val reportPosition by rememberUpdatedState(onBrowsePosition)
    val reportHandled by rememberUpdatedState(onRestoreHandled)
    val selectedIndex = remember(channels, selectedChannelId) { channels.indexOfFirst { it.id == selectedChannelId } }
    BoxWithConstraints(modifier.onFocusChanged { gridHasFocus = it.hasFocus }.focusGroup()) {
        val density = LocalDensity.current
        val fontScale = density.fontScale.coerceAtLeast(1f)
        val columnCount = TvGridLayout.columns(maxWidth.value, columns ?: 4, (if (columns == null) 220f else 164f) * fontScale)
        val rows = TvGridLayout.rows(maxHeight.value, 96f * fontScale)
        LaunchedEffect(selectedChannelId, selectedIndex, restore?.token) {
            if (selectedChannelId == null) restoredId = null
            if (selectedIndex >= 0 && (restore != null || restoredId != selectedChannelId)) {
                restoring = true
                try {
                    val position = restore?.position
                    val anchor = position?.anchorId?.let { id -> channels.indexOfFirst { it.id == id } } ?: -1
                    val target = HomeGridRestorePolicy.target(anchor, selectedIndex, columnCount, rows.count,
                        (position?.offsetPx ?: 0).coerceAtMost(((rows.height + 12f) * density.density).toInt().coerceAtLeast(1) - 1),
                        position?.columns ?: 0)
                    grid.scrollToItem(target.index, target.offset)
                    snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex } }.first { it }
                    withFrameNanos { }; runCatching { restoreFocus.requestFocus() }
                    restoredId = selectedChannelId
                    restore?.let { reportHandled(it.token) }
                } finally { restoring = false }
            }
        }
        LaunchedEffect(grid, columnCount) {
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
        LaunchedEffect(columnCount) {
            if (gridHasFocus && focusedIndex >= 0 && !restoring) grid.scrollToItem(TvGridLayout.rowStart(focusedIndex, columnCount))
        }
        // Focus bring-into-view owns normal scrolling. A second idle-time snap fights it.
        LazyVerticalGrid(columns = GridCells.Fixed(columnCount), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(channels, key = { _, channel -> channel.id }, contentType = { _, _ -> "channel" }) { index, channel ->
                ChannelCard(channel, { onChannelClick(channel) }, Modifier.height(rows.height.dp)
                    .onFocusChanged { if (it.isFocused) { focusedIndex = index; focusedId = channel.id } }
                    .then(if (index == selectedIndex) Modifier.focusRequester(restoreFocus) else Modifier), showGroup)
            }
        }
    }
}

private data class GridObservation(val first: Int, val offset: Int, val focused: String?, val busy: Boolean)