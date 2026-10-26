package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.player.vod.VodTrackChoice
import com.yourapp.iptv.player.vod.VodTrackList
import com.yourapp.iptv.ui.component.common.AppButton

@Composable
internal fun VodTrackDialog(title: String, tracks: VodTrackList, subtitles: Boolean, labels: VodPlaybackLabels,
    onSelect: (VodTrackChoice?) -> Unit, onOff: () -> Unit, onClose: () -> Unit) {
    val initial = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.large,modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp).testTag("vod-track-dialog"),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title,style = MaterialTheme.typography.titleLarge)
                AppButton((if (tracks.automatic) "✓ " else "") + labels.automatic(),{ onSelect(null) },
                    Modifier.fillMaxWidth().focusRequester(initial).testTag("vod-track-auto"))
                if (subtitles) AppButton((if (tracks.disabled) "✓ " else "") + labels.off(),onOff,
                    Modifier.fillMaxWidth().testTag("vod-track-off"))
                if (tracks.choices.isEmpty()) Text(labels.none())
                LazyColumn(Modifier.heightIn(max = 240.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(tracks.choices) { index, choice ->
                        AppButton((if (choice.chosen) "✓ " else "") + choice.name,{ onSelect(choice) },
                            Modifier.fillMaxWidth().testTag("vod-track-$index"))
                    }
                }
                AppButton(labels.close(),onClose,Modifier.testTag("vod-track-close"))
            }
        }
        LaunchedEffect(Unit) { initial.requestFocus() }
    }
}