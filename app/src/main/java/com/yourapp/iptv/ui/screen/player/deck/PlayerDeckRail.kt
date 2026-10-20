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

@Composable
internal fun PlayerDeckRail(channels: List<Channel>, currentIndex: Int, currentId: String?,
    appearance: PlayerAppearance, focusEpoch: Int, focusMore: Boolean,
    onMore: () -> Unit, onSelect: (String) -> Unit, onFocusChannel: (String?) -> Unit) {
    val window = remember(channels, currentIndex) { PlayerDeckWindow.channels(channels, currentIndex) }
    val list = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    val moreFocus = remember { FocusRequester() }
    LaunchedEffect(currentId, focusEpoch) {
        val index = window.indexOfFirst { it.channel.id == currentId }
        if (focusMore || index < 0) {
            list.scrollToItem(0); withFrameNanos { }; runCatching { moreFocus.requestFocus() }
        } else {
            list.scrollToItem((index - 1).coerceAtLeast(0))
            withFrameNanos { }; runCatching { selectedFocus.requestFocus() }
        }
    }
    LazyRow(state = list, modifier = Modifier.fillMaxWidth().height(PlayerTileLayout.height(appearance.showLogo, LocalDensity.current.fontScale).dp + 8.dp),
        contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("more") { DeckShortcutTile(DeckIcon.MORE, stringResource(R.string.player_more), true, onMore,
            Modifier.width(92.dp).fillParentMaxHeight().focusRequester(moreFocus)
                .onFocusChanged { if (it.isFocused) onFocusChannel(null) }) }
        items(window, key = { "channel:${it.channel.id}" }) { item ->
            val playing = item.channel.id == currentId
            DeckChannelTile(item, playing, appearance.showLogo,
                { onSelect(item.channel.id) }, Modifier.width(156.dp).fillParentMaxHeight()
                    .onFocusChanged { if (it.isFocused) onFocusChannel(item.channel.id) }
                    .then(if (playing) Modifier.focusRequester(selectedFocus) else Modifier))
        }
    }
}