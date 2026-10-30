package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.presentation.PlayerTileLayout
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.ui.presentation.PlayerDeckWindow
import com.yourapp.iptv.ui.component.common.FocusInputEpoch
import com.yourapp.iptv.ui.component.common.Resume
import com.yourapp.iptv.ui.screen.player.rememberPlayerFocus

@Composable
internal fun PlayerDeckRail(channels: List<Channel>, currentIndex: Int, currentId: String?,
    appearance: PlayerAppearance, focusEpoch: Int, focusMore: Boolean,
    onMore: () -> Unit, onSelect: (String) -> Unit, onFocusChannel: (String?) -> Unit) {
    val window = remember(channels, currentIndex) { PlayerDeckWindow.channels(channels, currentIndex) }
    val list = rememberLazyListState()
    val focus = rememberPlayerFocus()
    val keys = remember(window) { listOf("more") + window.map { "channel:${it.channel.id}" } }
    val preferred = if (focusMore || currentId == null) "more" else "channel:$currentId"
    focus.Resume(setOf("player-deck"), "player-deck", preferred)
    val inputEpoch = remember(currentId, focusEpoch, focusMore) { FocusInputEpoch.value }
    LaunchedEffect(currentId, focusEpoch, focusMore) {
        if (inputEpoch == FocusInputEpoch.value) focus.request("player-deck", preferred)
    }
    focus.Maintain("player-deck", keys)
    focus.Restore("player-deck", keys, scrollTo = { index ->
        if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
    })
    LazyRow(state = list, modifier = Modifier.fillMaxWidth().height(PlayerTileLayout.height(appearance.showLogo, LocalDensity.current.fontScale).dp + 8.dp),
        contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("more") { DeckShortcutTile(DeckIcon.MORE, stringResource(R.string.player_more), true, onMore,
            Modifier.width(92.dp).fillParentMaxHeight().then(focus.target("player-deck", "more", 0))
                .onFocusChanged { if (it.isFocused) onFocusChannel(null) }) }
        itemsIndexed(window, key = { _, item -> "channel:${item.channel.id}" }) { index, item ->
            val playing = item.channel.id == currentId
            DeckChannelTile(item, playing, appearance.showLogo,
                { onSelect(item.channel.id) }, Modifier.width(156.dp).fillParentMaxHeight()
                    .onFocusChanged { if (it.isFocused) onFocusChannel(item.channel.id) }
                    .then(focus.target("player-deck", "channel:${item.channel.id}", index + 1)))
        }
    }
}