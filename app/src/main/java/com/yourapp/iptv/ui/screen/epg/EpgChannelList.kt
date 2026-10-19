package com.yourapp.iptv.ui.screen.epg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.component.channel.FavoriteBadge
import com.yourapp.iptv.ui.state.EpgUiState
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
fun EpgChannelList(
    state: EpgUiState, request: EpgFocusRequest, onSelect: (String) -> Unit,
    onActivate: (String) -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier
) {
    val list = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(request.serial) {
        if (request.area == EpgFocusArea.CHANNELS && state.selectedIndex >= 0) {
            if (list.layoutInfo.visibleItemsInfo.none { it.index == state.selectedIndex }) {
                list.scrollToItem(state.selectedIndex)
            }
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        }
    }
    Surface(modifier, color = Color(0xFF101C32), contentColor = Color.White,
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxSize().padding(8.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("القنوات", style = MaterialTheme.typography.titleMedium)
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.queue.channels, key = { _, channel -> channel.id }) { index, channel ->
                    EpgChannelRow(channel, index + 1, channel.id == state.selectedChannelId,
                        onSelect = { onSelect(channel.id) }, onActivate = { onActivate(channel.id) },
                        modifier = if (channel.id == state.selectedChannelId) Modifier.focusRequester(selectedFocus)
                            else Modifier)
                }
            }
        }
    }
}

@Composable
private fun EpgChannelRow(
    channel: Channel, number: Int, selected: Boolean,
    onSelect: () -> Unit, onActivate: () -> Unit, modifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onActivate, modifier = modifier.fillMaxWidth().onFocusChanged {
        focused = it.isFocused
        if (it.isFocused) onSelect()
    }, shape = MaterialTheme.shapes.medium, contentColor = Color.White,
        color = if (focused) Color(0xFF164362) else if (selected) Color(0xFF163148) else Color(0xFF17243D),
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused || selected) NeonBlue else Color(0xFF2D3F58))) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = NeonBlue)
            ChannelLogo(channel.name, channel.logoUrl, Modifier.size(32.dp))
            FavoriteBadge(channel.isFavorite)
            Text(channel.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}