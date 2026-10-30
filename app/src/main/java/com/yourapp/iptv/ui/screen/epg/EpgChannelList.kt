package com.yourapp.iptv.ui.screen.epg

import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.component.channel.FavoriteBadge
import com.yourapp.iptv.ui.state.EpgUiState
import com.yourapp.iptv.ui.component.common.ContextFocus

@Composable
internal fun EpgChannelList(
    state: EpgUiState, focus: ContextFocus, onSelect: (String) -> Unit,
    onActivate: (String) -> Unit, modifier: Modifier = Modifier
) {
    val list = rememberLazyListState()
    val keys = remember(state.queue) { state.queue.channels.map { it.id } }
    focus.Restore(EPG_CHANNELS, keys, ready = !state.isLoading && !state.isFiltering,
        fallbackKey = state.selectedChannelId,
        onEmpty = { focus.request(EPG_FILTERS, "query") }, scrollTo = { index ->
            if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
        })
    Surface(modifier, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxSize().padding(8.dp).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ui_channels), style = MaterialTheme.typography.titleMedium)
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.queue.channels, key = { _, channel -> channel.id }) { index, channel ->
                    EpgChannelRow(channel, index + 1, channel.id == state.selectedChannelId,
                        onSelect = { onSelect(channel.id) }, onActivate = {
                            focus.record(EPG_CHANNELS, channel.id, index)
                            onActivate(channel.id)
                        }, modifier = focus.target(EPG_CHANNELS, channel.id, index)
                            .testTag("epg-channel-${channel.id}"))
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
    }, shape = MaterialTheme.shapes.medium, contentColor = MaterialTheme.colorScheme.onSurface,
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ChannelLogo(channel.name, channel.logoUrl, Modifier.size(32.dp))
            FavoriteBadge(channel.isFavorite)
            Text(channel.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}