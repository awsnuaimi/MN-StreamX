@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.yourapp.iptv.ui.screen.player

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.component.channel.FavoriteBadge
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.Resume

@Composable
fun PlayerChannelPanel(
    channels: List<Channel>,
    currentIndex: Int,
    group: String?,
    query: String,
    favoritesOnly: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onRemoteKey: (KeyEvent, Boolean) -> Boolean
) {
    val focus = rememberPlayerFocus()
    val currentId = channels.getOrNull(currentIndex)?.id
    val context = "player-channels:$currentId"
    val controls = "$context:controls"
    val keys = remember(channels) { channels.map { it.id } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        focus.Resume(setOf(context, controls), context, currentId.takeIf { focus.bookmarks.get(context) == null })
        focus.Maintain(context, keys)
        focus.Restore(context, keys, ready = windowFocused,
            onEmpty = { focus.request(controls, "close") }, scrollTo = { index ->
                if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
            })
        focus.Restore(controls, listOf("close"), ready = windowFocused)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .then(focus.inputModifier())
            .onPreviewKeyEvent { onRemoteKey(it.nativeKeyEvent, false) }) {
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    .widthIn(max = 460.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.player_channels) + " · ${channels.size}", style = MaterialTheme.typography.titleLarge)
                            Text((if (favoritesOnly) "★ " + stringResource(R.string.player_favorites) + " · " else "") + (group ?: stringResource(R.string.player_all_categories)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                        AppButton(stringResource(R.string.player_close), onDismiss,
                            focus.target(controls, "close", 0).testTag("player-channels-close"))
                    }
                    if (query.isNotBlank()) Text(stringResource(R.string.player_search, query), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    if (channels.isEmpty()) Text(stringResource(R.string.player_empty_list))
                    LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("player-channel-list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                            PlayerChannelRow(channel, index + 1, index == currentIndex,
                                onClick = { focus.record(context, channel.id, index); onSelect(channel.id) },
                                modifier = focus.target(context, channel.id, index).testTag("player-channel-${channel.id}"))
                        }
                    }
                    Text(stringResource(R.string.player_list_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlayerChannelRow(
    channel: Channel, number: Int, playing: Boolean, onClick: () -> Unit, modifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.medium,
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else if (playing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused || playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(number.toString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            ChannelLogo(channel.name, channel.logoUrl, Modifier.size(40.dp))
            FavoriteBadge(channel.isFavorite)
            Column(Modifier.weight(1f)) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
                if (playing) Text(stringResource(R.string.player_playing), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}