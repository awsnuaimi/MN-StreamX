package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourapp.iptv.domain.model.VodEpisode
import com.yourapp.iptv.viewmodel.VodPlaybackState

/** Side drawer uses only the episodes actually supplied by this series' source. */
@Composable
internal fun VodEpisodePanel(item: VodPlaybackState.Ready, labels: VodPlaybackLabels,
    onSelect: (VodEpisode) -> Unit, onClose: () -> Unit) {
    val visible = remember(item.episodes,item.id) {
        item.episodes.filter { it.season ==
            (item.episodes.firstOrNull { episode -> episode.id == item.id }?.season
                ?: item.episodes.firstOrNull()?.season) }
    }
    val selected = visible.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
    val initial = remember(item.id) { FocusRequester() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(16.dp),contentAlignment = Alignment.CenterEnd) {
            VodCinemaPanel(Modifier.widthIn(max = 400.dp).fillMaxWidth().fillMaxHeight(.83f)
                .testTag("vod-episode-panel")) {
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    Text(labels.season(visible.firstOrNull()?.season ?: 1),
                        style = MaterialTheme.typography.titleLarge,modifier = Modifier.weight(1f))
                    Text("${visible.size} ${labels.episodes()}",style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.weight(1f),state = list,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(visible,key = { _,episode -> episode.id }) { index,episode ->
                        val isCurrent = episode.id == item.id
                        VodChoiceRow("${labels.episode(episode.number)} · ${episode.name}",isCurrent,
                            { if (isCurrent) onClose() else onSelect(episode) },
                            Modifier.fillMaxWidth().then(if (index == selected)
                                Modifier.focusRequester(initial) else Modifier)
                                .testTag("vod-episode-${episode.id}"),glyph = if (isCurrent) null else VodGlyph.PLAY)
                    }
                }
                VodControlButton(labels.close(),VodGlyph.BACK,onClose,
                    Modifier.fillMaxWidth().testTag("vod-episodes-close"))
            }
        }
        LaunchedEffect(item.id,visible.size) {
            if (visible.isNotEmpty()) {
                list.scrollToItem(selected)
                withFrameNanos { };initial.requestFocus()
            }
        }
    }
}