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

@Composable
internal fun VodDetailsDialog(state: VodUiState, chosen: VodItem, labels: VodLabels,
    progress: List<VodProgressEntry>, onDismiss: () -> Unit, onRetry: () -> Unit, onPlay: (VodKind, String, String, String, String?) -> Unit) {
    val item = state.details?.item ?: chosen
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Button(onClick = { onPlay(VodKind.MOVIE, item.id, item.extension, item.name, null) }) {
                        Text(labels.play())
                    }
                } else {
                    Text(labels.episodes(), style = MaterialTheme.typography.titleMedium)
                    if (!state.detailLoading && state.details?.seasons.isNullOrEmpty()) {
                        Text(if (state.detailError) labels.failed() else labels.empty())
                        if (state.detailError) TextButton(onClick = onRetry) { Text(labels.tryAgain()) }
                    }
                    LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        state.details?.seasons.orEmpty().forEach { season ->
                            item(key = "s${season.number}") {
                                Text("${labels.text("الموسم", "Season", "Staffel")} ${season.number} (${season.episodes.size})",
                                    style = MaterialTheme.typography.titleSmall)
                            }
                            items(season.episodes, key = { "${season.number}-${it.id}" }) { episode ->
                                OutlinedButton(onClick = {
                                    onPlay(VodKind.SERIES, episode.id, episode.extension, episode.name, chosen.id)
                                }, modifier = Modifier.fillMaxWidth()) {
                                    val position = VodProgress.find(progress, VodKind.SERIES, episode.id)
                                    Text(buildString {
                                        append("${episode.number}. ${episode.name}")
                                        if (position != null) append("  •  ${labels.resume()} ${labels.position(position.positionMs)}")
                                    }, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text(labels.back()) }
            }
        }
    }
}