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
import com.yourapp.iptv.ui.component.channel.ChannelCard
import com.yourapp.iptv.ui.presentation.TvGridLayout

@Composable
fun TvChannelGrid(channels: List<Channel>, selectedChannelId: String?, onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier, columns: Int? = null, showGroup: Boolean = true) {
    val grid = rememberLazyGridState()
    val restoreFocus = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var gridHasFocus by remember { mutableStateOf(false) }
    var restoredId by remember { mutableStateOf<String?>(null) }
    val selectedIndex = remember(channels, selectedChannelId) { channels.indexOfFirst { it.id == selectedChannelId } }
    BoxWithConstraints(modifier.onFocusChanged { gridHasFocus = it.hasFocus }.focusGroup()) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val columnCount = TvGridLayout.columns(maxWidth.value, columns ?: 4, (if (columns == null) 220f else 164f) * fontScale)
        val rows = TvGridLayout.rows(maxHeight.value, 96f * fontScale)
        LaunchedEffect(selectedChannelId, selectedIndex) {
            if (selectedChannelId == null) restoredId = null
            if (selectedIndex >= 0 && restoredId != selectedChannelId) {
                grid.scrollToItem(TvGridLayout.rowStart(selectedIndex, columnCount))
                withFrameNanos { }; runCatching { restoreFocus.requestFocus() }
                restoredId = selectedChannelId
            }
        }
        LaunchedEffect(columnCount) {
            if (gridHasFocus && focusedIndex >= 0) grid.scrollToItem(TvGridLayout.rowStart(focusedIndex, columnCount))
        }
        // Focus bring-into-view owns normal scrolling. A second idle-time snap fights it.
        LazyVerticalGrid(columns = GridCells.Fixed(columnCount), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(channels, key = { _, channel -> channel.id }, contentType = { _, _ -> "channel" }) { index, channel ->
                ChannelCard(channel, { onChannelClick(channel) }, Modifier.height(rows.height.dp)
                    .onFocusChanged { if (it.isFocused) focusedIndex = index }
                    .then(if (index == selectedIndex) Modifier.focusRequester(restoreFocus) else Modifier), showGroup)
            }
        }
    }
}