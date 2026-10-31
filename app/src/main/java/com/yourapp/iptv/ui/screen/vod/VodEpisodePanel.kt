package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.yourapp.iptv.domain.model.VodProgressEntry
import com.yourapp.iptv.viewmodel.VodPlaybackState

/** Right-hand episode drawer: real provider episodes, with explicit season selection. */
@Composable
internal fun VodEpisodePanel(item: VodPlaybackState.Ready, labels: VodPlaybackLabels,
    onSelect: (VodEpisode) -> Unit, onClose: () -> Unit,
    progressRows: List<VodProgressEntry> = emptyList()) {
    val seasons = remember(item.episodes) { vodSeasonNumbers(item.episodes) }
    val initialSeason = remember(item.episodes,item.id) { vodInitialSeason(item.episodes,item.id) }
    var season by remember(item.sourceKey,item.seriesId,item.id) { mutableStateOf(initialSeason) }
    val fractions = remember(item.seriesId,item.episodes,progressRows) {
        vodEpisodeProgress(item.episodes,item.seriesId,progressRows)
    }
    val visible = remember(item.episodes,season) {
        season?.let { vodEpisodesInSeason(item.episodes,it) }.orEmpty()
    }
    val currentIndex = remember(visible,item.id) { visible.indexOfFirst { it.id == item.id } }
    val initialIndex = currentIndex.coerceAtLeast(0)
    val initial = remember(item.sourceKey,item.id) { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    // A season switch must not steal focus from the season chip the viewer pressed.
    LaunchedEffect(item.episodes,initialSeason) {
        if (season !in seasons) season = initialSeason
    }
    LaunchedEffect(season) {
        if (visible.isNotEmpty()) list.scrollToItem(if (season == initialSeason) initialIndex else 0)
    }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp),contentAlignment = Alignment.CenterEnd) {
            val drawerWidth = if (maxWidth < 680.dp) maxWidth - 8.dp
                else (maxWidth * .48f).coerceAtMost(460.dp)
            VodCinemaPanel(Modifier.width(drawerWidth).fillMaxHeight(.86f)
                .testTag("vod-episode-panel")) {
                Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
                    Text(labels.episodes(),style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f))
                    Text("${visible.size}",style = MaterialTheme.typography.bodySmall)
                }
                if (seasons.size > 1) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .testTag("vod-episode-seasons"),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        seasons.forEach { number ->
                            VodChoiceRow(labels.season(number),season == number,{ season = number },
                                Modifier.width(142.dp).testTag("vod-season-$number"))
                        }
                    }
                } else if (season != null) {
                    Text(labels.season(season!!),style = MaterialTheme.typography.titleMedium)
                }
                LazyColumn(Modifier.weight(1f).testTag("vod-episode-list"),state = list,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(visible,key = { _,episode -> episode.id }) { index,episode ->
                        val isCurrent = episode.id == item.id
                        VodChoiceRow("${labels.episode(episode.number)} · ${episode.name}",isCurrent,
                            { if (isCurrent) onClose() else onSelect(episode) },
                            Modifier.fillMaxWidth().then(if (index == initialIndex && season == initialSeason)
                                Modifier.focusRequester(initial) else Modifier)
                                .testTag("vod-episode-${episode.id}"),glyph = if (isCurrent) null else VodGlyph.PLAY,
                            progress = fractions[episode.id],progressTag = "vod-episode-progress-${episode.id}")
                    }
                }
                VodControlButton(labels.close(),VodGlyph.BACK,onClose,
                    Modifier.fillMaxWidth().focusRequester(closeFocus).testTag("vod-episodes-close"))
            }
        }
        // On opening, focus the current episode; do not refocus when switching seasons.
        LaunchedEffect(item.sourceKey,item.id) {
            if (visible.isNotEmpty()) {
                list.scrollToItem(initialIndex)
                withFrameNanos { };initial.requestFocus()
            } else {
                withFrameNanos { };closeFocus.requestFocus()
            }
        }
    }
}