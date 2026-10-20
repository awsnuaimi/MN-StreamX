package com.yourapp.iptv.ui.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelCard
import com.yourapp.iptv.ui.presentation.TvGridLayout
import kotlinx.coroutines.flow.collect

@Composable
fun TvChannelGrid(channels: List<Channel>, selectedChannelId: String?, onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier, columns: Int? = null, showGroup: Boolean = true) {
    val grid = rememberLazyGridState()
    val restoreFocus = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val selectedIndex = channels.indexOfFirst { it.id == selectedChannelId }
    BoxWithConstraints(modifier) {
        val columnCount = columns?.coerceIn(2, 4) ?: (maxWidth.value / 220f).toInt().coerceAtLeast(1)
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val rows = TvGridLayout.rows(maxHeight.value, 96f * fontScale)
        LaunchedEffect(selectedChannelId, selectedIndex, columnCount) {
            if (selectedIndex >= 0) {
                grid.scrollToItem(TvGridLayout.rowStart(selectedIndex, columnCount))
                withFrameNanos { }; runCatching { restoreFocus.requestFocus() }
            }
        }
        LaunchedEffect(grid, columnCount, channels.size, rows.height) {
            var blockedPosition: Pair<Int, Int>? = null
            snapshotFlow { Triple(grid.isScrollInProgress, grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset) }
                .collect { (scrolling, first, offset) ->
                    if (!scrolling) {
                        val target = TvGridLayout.snapTarget(first, offset, focusedIndex, columnCount, channels.size)
                        val position = first to offset
                        if (target != null && position != blockedPosition) {
                            grid.scrollToItem(target)
                            val reached = grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset
                            // Only block an immovable edge; revisiting a previous offset must still snap.
                            blockedPosition = if (reached == position) position else null
                        }
                    }
                }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(columnCount), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                ChannelCard(channel, { onChannelClick(channel) }, Modifier.height(rows.height.dp)
                    .onFocusChanged { if (it.isFocused) focusedIndex = index }
                    .then(if (index == selectedIndex) Modifier.focusRequester(restoreFocus) else Modifier), showGroup)
            }
        }
    }
}