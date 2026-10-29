package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.player.vod.VodTrackChoice
import com.yourapp.iptv.player.vod.VodTrackList

@Composable
internal fun VodTrackDialog(title: String, tracks: VodTrackList, subtitles: Boolean, labels: VodPlaybackLabels,
    onSelect: (VodTrackChoice?) -> Unit, onOff: () -> Unit, onClose: () -> Unit) {
    val initial = remember { FocusRequester() }
    val offset = if (subtitles) 2 else 1
    val selected = when {
        tracks.disabled && subtitles -> 1
        tracks.automatic -> 0
        else -> tracks.choices.indexOfFirst { it.chosen }.takeIf { it >= 0 }?.plus(offset) ?: 0
    }
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    fun focus(index: Int) = if (index == selected) Modifier.focusRequester(initial) else Modifier
    // New dialog windows may start in touch mode; TV choices still need an initial focus.
    Dialog(onDismissRequest = onClose) {
        VodCinemaPanel(Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = 440.dp).testTag("vod-track-dialog")) {
                VodPanelTitle(title,if (subtitles) VodGlyph.SUBTITLES else VodGlyph.AUDIO)
                if (tracks.choices.isEmpty()) Text(labels.none())
                LazyColumn(Modifier.weight(1f,fill = false).heightIn(max = 280.dp),state = scroll,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { VodChoiceRow(labels.automatic(),tracks.automatic,{ onSelect(null) },
                        Modifier.fillMaxWidth().focusProperties { canFocus = true }.then(focus(0)).testTag("vod-track-auto")) }
                    if (subtitles) item { VodChoiceRow(labels.off(),tracks.disabled,onOff,
                        Modifier.fillMaxWidth().focusProperties { canFocus = true }.then(focus(1)).testTag("vod-track-off")) }
                    itemsIndexed(tracks.choices) { index,choice ->
                        VodChoiceRow(choice.name,choice.chosen,{ onSelect(choice) },
                            Modifier.fillMaxWidth().focusProperties { canFocus = true }.then(focus(index+offset)).testTag("vod-track-$index"))
                    }
                }
                VodControlButton(labels.close(),VodGlyph.BACK,onClose,Modifier.focusProperties { canFocus = true }.testTag("vod-track-close"))
        }
        LaunchedEffect(selected,tracks.choices.map { it.group to it.index }) {
            scroll.scrollToItem(selected)
            withFrameNanos { };initial.requestFocus()
        }
    }
}