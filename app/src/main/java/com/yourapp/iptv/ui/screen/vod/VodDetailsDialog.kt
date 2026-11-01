package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.viewmodel.VodUiState
import androidx.compose.ui.platform.testTag
import com.yourapp.iptv.ui.component.common.*

@Composable
internal fun VodDetailsDialog(state: VodUiState, chosen: VodItem, labels: VodLabels,
    progress: List<VodProgressEntry>, onDismiss: () -> Unit, onRetry: () -> Unit,
    completed: List<VodCompletedEpisode> = emptyList(),
    onPlay: (VodKind, String, String, String, String?) -> Unit) {
    val item = state.details?.item ?: chosen
    val focus = rememberContextFocus("details:${state.sourceKey}:${state.kind}:${chosen.id}")
    val seasons = state.details?.seasons.orEmpty()
    val episodes = seasons.flatMap { season -> season.episodes.map { "episode:${season.number}:${it.id}" } }
    val recommendation = remember(state.kind, chosen.id, seasons, progress, completed) {
        if (state.kind == VodKind.SERIES) VodSeriesContinuation.choose(chosen.id, seasons, progress, completed)
        else null
    }
    val progressFractions = remember(seasons, chosen.id, progress) {
        vodEpisodeProgress(seasons.flatMap { it.episodes }, chosen.id, progress)
    }
    val watched = remember(seasons, chosen.id, completed, progress) {
        vodEpisodeWatched(seasons.flatMap { it.episodes }, chosen.id, completed, progress)
    }
    val keys = buildList {
        if (state.kind == VodKind.MOVIE) add("play") else {
            if (recommendation != null) add("series-recommend")
            addAll(episodes)
        }
        if (state.kind == VodKind.SERIES && !state.detailLoading && state.details?.seasons.isNullOrEmpty() && state.detailError) add("retry")
        add("close")
    }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    val rows = state.details?.seasons.orEmpty().flatMap { season -> listOf("season:${season.number}") + season.episodes.map { "episode:${season.number}:${it.id}" } }
    Dialog(onDismissRequest = onDismiss) {
        focus.Resume(setOf("dialog"), "dialog", keys.firstOrNull())
        focus.Restore("dialog", keys, scrollTo = { index ->
            val row = rows.indexOf(keys[index])
            if (row >= 0) list.scrollToItem(row)
        })
        focus.Maintain("dialog", keys)
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Column(Modifier.then(focus.inputModifier()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                if (state.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                item.year?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                item.rating?.let { Text("★ $it", style = MaterialTheme.typography.labelMedium) }
                item.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(it.take(650), style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                }
                if (state.kind == VodKind.MOVIE) {
                    VodProgress.find(progress, VodKind.MOVIE, item.id)?.let { saved ->
                        Text("${labels.resume()}: ${labels.position(saved.positionMs)}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { onPlay(VodKind.MOVIE, item.id, item.extension, item.name, null) },
                        modifier = focus.target("dialog", "play", 0).tvFocusIndicator().testTag("vod-details-play")) {
                        Text(labels.play())
                    }
                } else {
                    recommendation?.let { choice ->
                        val action = when (choice.kind) {
                            VodSeriesChoiceKind.RESUME -> labels.seriesResume()
                            VodSeriesChoiceKind.START -> labels.seriesStart()
                            VodSeriesChoiceKind.REWATCH -> labels.seriesReplay()
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Button(onClick = { onPlay(VodKind.SERIES, choice.episode.id,
                                choice.episode.extension, choice.episode.name, chosen.id) },
                                modifier = focus.target("dialog", "series-recommend", 0)
                                    .tvFocusIndicator().testTag("vod-series-recommend")) {
                                Text(action)
                            }
                            Text("${labels.seasonEpisode(choice.episode.season, choice.episode.number)} · ${choice.episode.name}",
                                style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            if (choice.kind == VodSeriesChoiceKind.RESUME) {
                                Text("${labels.resume()}: ${labels.position(choice.positionMs)}",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text(labels.episodes(), style = MaterialTheme.typography.titleMedium)
                    if (!state.detailLoading && state.details?.seasons.isNullOrEmpty()) {
                        Text(if (state.detailError) labels.failed() else labels.empty())
                        if (state.detailError) TextButton(onClick = onRetry, modifier = focus.target("dialog", "retry",
                            episodes.size + if (recommendation != null) 1 else 0)
                            .tvFocusIndicator()) { Text(labels.tryAgain()) }
                    }
                    LazyColumn(Modifier.weight(1f, fill = false), state = list, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        state.details?.seasons.orEmpty().forEach { season ->
                            item(key = "s${season.number}") {
                                Text("${labels.text("الموسم", "Season", "Staffel")} ${season.number} (${season.episodes.size})",
                                    style = MaterialTheme.typography.titleSmall)
                            }
                            items(season.episodes, key = { "${season.number}-${it.id}" }) { episode ->
                                OutlinedButton(onClick = {
                                    onPlay(VodKind.SERIES, episode.id, episode.extension, episode.name, chosen.id)
                                }, modifier = Modifier.fillMaxWidth().then(focus.target("dialog", "episode:${season.number}:${episode.id}",
                                    episodes.indexOf("episode:${season.number}:${episode.id}") +
                                        if (recommendation != null) 1 else 0)).tvFocusIndicator()
                                    .testTag("vod-episode-${episode.id}")) {
                                    val position = progress.firstOrNull {
                                        it.kind == VodKind.SERIES && it.seriesId == chosen.id &&
                                            it.id == episode.id && VodProgress.resumable(it)
                                    }
                                    val isWatched = episode.id in watched
                                    val isResumable = episode.id in progressFractions && !isWatched
                                    Text(buildString {
                                        append("${episode.number}. ${episode.name}")
                                        when {
                                            isResumable -> append("  •  ${labels.seriesResume()} ${labels.position(position?.positionMs ?: 0L)}")
                                            isWatched -> append("  •  ${labels.watched()}")
                                            else -> Unit // Preserve the existing episode title and UI test semantics.
                                        }
                                    }, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = focus.target("dialog", "close", keys.lastIndex)
                    .tvFocusIndicator().testTag("vod-details-close")) { Text(labels.back()) }
            }
        }
    }
}