package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.ContextFocus
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

/** Season grouping preserves the existing episode keys and lazy scroll mapping. */
@Composable
internal fun VodDetailsEpisodes(seasons: List<VodSeason>, seriesId: String, labels: VodLabels,
    progress: List<VodProgressEntry>, completed: List<VodCompletedEpisode>, list: LazyListState,
    focus: ContextFocus, offset: Int, onPlay: (VodKind, String, String, String, String?) -> Unit,
    modifier: Modifier = Modifier) {
    val all = remember(seasons) { seasons.flatMap { it.episodes } }
    val fractions = remember(all, seriesId, progress) { vodEpisodeProgress(all, seriesId, progress) }
    val watched = remember(all, seriesId, completed, progress) { vodEpisodeWatched(all, seriesId, completed, progress) }
    val episodeKeys = remember(seasons) {
        seasons.flatMap { season -> season.episodes.map { "episode:${season.number}:${it.id}" } }
    }
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier.testTag("vod-details-episodes"), state = list,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        seasons.forEach { season ->
            item(key = "s${season.number}") {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${labels.text("الموسم", "Season", "Staffel")} ${season.number}",
                        color = colors.onSurface, style = MaterialTheme.typography.titleSmall)
                    Text("${season.episodes.count { it.id in watched }} / ${season.episodes.size}",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            items(season.episodes, key = { "${season.number}-${it.id}" }) { episode ->
                val key = "episode:${season.number}:${episode.id}"
                val fraction = fractions[episode.id]
                val isWatched = episode.id in watched
                val isResumable = fraction != null && !isWatched
                val saved = if (isResumable) progress.firstOrNull {
                    it.kind == VodKind.SERIES && it.seriesId == seriesId && it.id == episode.id && VodProgress.resumable(it)
                } else null
                var focused by remember { mutableStateOf(false) }
                Surface(onClick = { onPlay(VodKind.SERIES, episode.id, episode.extension, episode.name, seriesId) },
                    modifier = Modifier.fillMaxWidth().then(focus.target("dialog", key,
                        episodeKeys.indexOf(key) + offset)).onFocusChanged { focused = it.isFocused }
                        .tvFocusIndicator().testTag("vod-episode-${episode.id}"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (focused) colors.primaryContainer else colors.surfaceContainerHigh,
                    border = BorderStroke(if (focused) 2.dp else 1.dp,
                        if (focused) colors.primary else colors.outlineVariant.copy(alpha = .35f))) {
                    Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.primary)) {
                                    append("${episode.number}.")
                                }
                                append(" ${episode.name}")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface
                        )
                        val status = when {
                            isResumable -> "${labels.seriesResume()} ${labels.position(saved?.positionMs ?: 0L)}"
                            isWatched -> labels.watched()
                            else -> null // Keep unseen-episode text semantics unchanged.
                        }
                        status?.let {
                            Text(it, color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        if (isResumable || isWatched) LinearProgressIndicator(
                            progress = { if (isWatched) 1f else fraction ?: 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = colors.primary, trackColor = colors.outlineVariant.copy(alpha = .28f))
                    }
                }
            }
        }
    }
}