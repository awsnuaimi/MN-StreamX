package com.yourapp.iptv.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelCard

@Composable
fun TvChannelGrid(
    channels: List<Channel>,
    selectedChannelId: String?,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val grid = rememberLazyGridState()
    val restoreFocus = remember { FocusRequester() }
    val selectedIndex = channels.indexOfFirst { it.id == selectedChannelId }
    LaunchedEffect(selectedChannelId) {
        if (selectedIndex >= 0) {
            grid.scrollToItem(selectedIndex)
            withFrameNanos { }
            runCatching { restoreFocus.requestFocus() }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        state = grid,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            ChannelCard(
                channel = channel,
                onClick = { onChannelClick(channel) },
                modifier = if (index == selectedIndex) Modifier.focusRequester(restoreFocus) else Modifier
            )
        }
    }
}