package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.*
import com.yourapp.iptv.viewmodel.VodUiState

/** Cinematic details; only its layout changes, playback and focus identities remain stable. */
@Composable
internal fun VodDetailsDialog(state: VodUiState, chosen: VodItem, labels: VodLabels,
    progress: List<VodProgressEntry>, onDismiss: () -> Unit, onRetry: () -> Unit,
    completed: List<VodCompletedEpisode> = emptyList(),
    onPlay: (VodKind, String, String, String, String?) -> Unit) {
    val item = state.details?.item ?: chosen
    val focus = rememberContextFocus("details:${state.sourceKey}:${state.kind}:${chosen.id}")
    val seasons = state.details?.seasons.orEmpty()
    val episodeKeys = remember(seasons) {
        seasons.flatMap { season -> season.episodes.map { "episode:${season.number}:${it.id}" } }
    }
    val seasonKeys = remember(state.kind, seasons) {
        if (state.kind == VodKind.SERIES) VodDetailsSeasonNavigation.keys(seasons) else emptyList()
    }
    val recommendation = remember(state.kind, chosen.id, seasons, progress, completed) {
        if (state.kind == VodKind.SERIES) VodSeriesContinuation.choose(chosen.id, seasons, progress, completed)
        else null
    }
    val keys = buildList {
        if (state.kind == VodKind.MOVIE) add("play") else {
            if (recommendation != null) add("series-recommend")
            addAll(seasonKeys)
            addAll(episodeKeys)
        }
        if (state.kind == VodKind.SERIES && !state.detailLoading &&
            state.details?.seasons.isNullOrEmpty() && state.detailError) add("retry")
        add("close")
    }
    val list = rememberLazyListState()
    val rows = remember(seasons) {
        seasons.flatMap { season ->
            listOf("season:${season.number}") + season.episodes.map { "episode:${season.number}:${it.id}" }
        }
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val wide = VodDetailLayout.split(maxWidth.value)
            focus.Resume(setOf("dialog"), "dialog", keys.firstOrNull())
            focus.Restore("dialog", keys, scrollTo = { index ->
                val row = rows.indexOf(keys[index])
                if (row >= 0) list.scrollToItem(row)
            })
            focus.Maintain("dialog", keys)
            Surface(modifier = Modifier.fillMaxWidth(VodDetailLayout.panelFraction(maxWidth.value))
                .height((maxHeight * .9f).coerceAtMost(640.dp)).testTag("vod-details-dialog"),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 15.dp) {
                if (wide) {
                    Row(Modifier.fillMaxSize().then(focus.inputModifier()).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        VodDetailsArtwork(item, Modifier.width(202.dp).fillMaxHeight())
                        VodDetailsBody(state, item, chosen, labels, progress, completed,
                            seasons, recommendation, seasonKeys, episodeKeys.size, focus, list,
                            onDismiss, onRetry, onPlay, Modifier.weight(1f), showMetadata = true)
                    }
                } else {
                    Column(Modifier.fillMaxSize().then(focus.inputModifier()).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth().height(118.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            VodDetailsArtwork(item, Modifier.width(88.dp).fillMaxHeight())
                            VodDetailsMetadata(item, state.kind, labels, Modifier.weight(1f), compact = true)
                        }
                        VodDetailsBody(state, item, chosen, labels, progress, completed,
                            seasons, recommendation, seasonKeys, episodeKeys.size, focus, list,
                            onDismiss, onRetry, onPlay, Modifier.weight(1f), showMetadata = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun VodDetailsBody(state: VodUiState, item: VodItem, chosen: VodItem, labels: VodLabels,
    progress: List<VodProgressEntry>, completed: List<VodCompletedEpisode>, seasons: List<VodSeason>,
    recommendation: VodSeriesChoice?, seasonKeys: List<String>, episodeCount: Int,
    focus: ContextFocus,
    list: androidx.compose.foundation.lazy.LazyListState, onDismiss: () -> Unit, onRetry: () -> Unit,
    onPlay: (VodKind, String, String, String, String?) -> Unit,
    modifier: Modifier = Modifier, showMetadata: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showMetadata) VodDetailsMetadata(item, state.kind, labels)
        if (state.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.kind == VodKind.MOVIE) {
            VodProgress.find(progress, VodKind.MOVIE, item.id)?.let { saved ->
                Text("${labels.resume()}: ${labels.position(saved.positionMs)}",
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Button(onClick = { onPlay(VodKind.MOVIE, item.id, item.extension, item.name, null) },
                modifier = focus.target("dialog", "play", 0).tvFocusIndicator(VodCutShape)
                    .testTag("vod-details-play"), shape = VodCutShape) { Text(labels.play()) }
        } else {
            recommendation?.let { choice ->
                val action = when (choice.kind) {
                    VodSeriesChoiceKind.RESUME -> labels.seriesResume()
                    VodSeriesChoiceKind.START -> labels.seriesStart()
                    VodSeriesChoiceKind.REWATCH -> labels.seriesReplay()
                }
                Button(onClick = { onPlay(VodKind.SERIES, choice.episode.id,
                    choice.episode.extension, choice.episode.name, chosen.id) },
                    modifier = focus.target("dialog", "series-recommend", 0)
                        .tvFocusIndicator(VodCutShape).testTag("vod-series-recommend"),
                    shape = VodCutShape) {
                    Text(action)
                }
                Text("${labels.seasonEpisode(choice.episode.season, choice.episode.number)} · ${choice.episode.name}",
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                    maxLines = 1)
                if (choice.kind == VodSeriesChoiceKind.RESUME)
                    Text("${labels.resume()}: ${labels.position(choice.positionMs)}",
                        style = MaterialTheme.typography.labelSmall, color = colors.primary)
            }
            Text("${labels.episodes()} · $episodeCount", style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface)
            if (seasonKeys.isNotEmpty()) {
                VodDetailsSeasonBar(seasons, labels, list, focus,
                    if (recommendation != null) 1 else 0)
            }
            if (!state.detailLoading && seasons.isEmpty()) {
                Text(if (state.detailError) labels.failed() else labels.empty(),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (state.detailError) TextButton(onClick = onRetry,
                    modifier = focus.target("dialog", "retry", episodeCount + seasonKeys.size +
                        if (recommendation != null) 1 else 0)
                        .tvFocusIndicator()) { Text(labels.tryAgain()) }
            }
            if (seasons.isNotEmpty()) VodDetailsEpisodes(seasons, chosen.id, labels, progress, completed,
                list, focus, seasonKeys.size + if (recommendation != null) 1 else 0, onPlay,
                Modifier.weight(1f))
        }
        if (state.kind == VodKind.MOVIE) Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss,
            modifier = focus.target("dialog", "close",
                episodeCount + seasonKeys.size +
                    (if (state.kind == VodKind.MOVIE || recommendation != null) 1 else 0) +
                    (if (state.kind == VodKind.SERIES && !state.detailLoading && seasons.isEmpty() && state.detailError) 1 else 0))
                .tvFocusIndicator().testTag("vod-details-close")) {
            Text(labels.back())
        }
    }
}