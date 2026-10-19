package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.ui.presentation.PlayerDeckWindow

@Composable
internal fun PlayerDeckRail(channels: List<Channel>, currentIndex: Int, currentId: String?, currentTitle: String?,
    appearance: PlayerAppearance, focusCardId: String?, focusEpoch: Int, dense: Boolean,
    onGuide: () -> Unit, onChannels: () -> Unit, onSelect: (String) -> Unit) {
    val window = remember(channels, currentIndex) { PlayerDeckWindow.channels(channels, currentIndex) }
    val list = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(currentId, focusCardId, focusEpoch) {
        val index = window.indexOfFirst { it.channel.id == focusCardId }
        if (focusCardId != null && focusCardId == currentId && index >= 0) {
            list.scrollToItem(index + 2)
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        }
    }
    LazyRow(state = list, modifier = Modifier.fillMaxWidth().height(if (dense) 98.dp else 108.dp),
        contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item("guide") { DeckShortcutTile(DeckIcon.GUIDE, "دليل البرامج", channels.isNotEmpty(), onGuide,
            Modifier.width(112.dp).fillParentMaxHeight()) }
        item("list") { DeckShortcutTile(DeckIcon.CHANNELS, "قائمة القنوات", channels.isNotEmpty(), onChannels,
            Modifier.width(112.dp).fillParentMaxHeight()) }
        items(window, key = { "channel:${it.channel.id}" }) { item ->
            val playing = item.channel.id == currentId
            val subtitle = if (playing) currentTitle ?: "تعمل الآن"
                else if (appearance.showGroup) item.channel.group ?: "قناة مباشرة" else "قناة مباشرة"
            DeckChannelTile(item, playing, subtitle, appearance.showLogo, { onSelect(item.channel.id) },
                Modifier.width(140.dp).fillParentMaxHeight()
                    .then(if (playing) Modifier.focusRequester(selectedFocus) else Modifier))
        }
    }
}