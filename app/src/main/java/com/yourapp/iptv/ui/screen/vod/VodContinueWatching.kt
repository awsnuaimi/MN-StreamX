package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.domain.model.VodProgressEntry
import com.yourapp.iptv.ui.component.common.*

/** Only locally saved progress and artwork from the already loaded catalog. */
@Composable
internal fun VodContinueWatching(entries: List<VodProgressEntry>, labels: VodLabels,
    focus: ContextFocus? = null, catalog: List<VodItem> = emptyList(),
    onPlay: (VodProgressEntry) -> Unit) {
    val list = rememberLazyListState()
    val keys = entries.map { "${it.kind}-${it.id}" }
    val posters = remember(catalog) { catalog.associateBy { it.id } }
    focus?.Restore("continued", keys, scrollTo = { list.scrollToItem(it) })
    focus?.Maintain("continued", keys)
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(labels.resume(), style = MaterialTheme.typography.titleMedium)
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(entries, key = { _, it -> "${it.kind}-${it.id}" }) { index, entry ->
                var focused by remember { mutableStateOf(false) }
                val poster = posters[entry.id]?.poster?.takeIf {
                    it.startsWith("https://") || it.startsWith("http://")
                }
                val context = LocalContext.current
                val request = remember(context, poster) {
                    poster?.let { ImageRequest.Builder(context).data(it).size(110, 160).build() }
                }
                Surface(onClick = { focus?.record("continued", keys[index], index); onPlay(entry) },
                    modifier = Modifier.width(224.dp).height(94.dp)
                        .then(focus?.target("continued", keys[index], index) ?: Modifier)
                        .tvFocusIndicator().onFocusChanged { focused = it.isFocused },
                    color = if (focused) colors.primaryContainer else colors.surfaceContainerLow,
                    border = if (focused) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
                    shape = RoundedCornerShape(11.dp)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (request != null) AsyncImage(model = request, contentDescription = null,
                            modifier = Modifier.width(63.dp).fillMaxHeight(), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = colors.onSurface, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            val fraction = if (entry.durationMs > 0L)
                                (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                            Text(labels.position(entry.positionMs), color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}