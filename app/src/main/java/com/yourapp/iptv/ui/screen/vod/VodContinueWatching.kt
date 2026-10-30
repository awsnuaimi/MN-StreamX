package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodProgressEntry
import com.yourapp.iptv.ui.component.common.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed

/** Only bounded, locally saved metadata; no catalog or network request. */
@Composable
internal fun VodContinueWatching(
    entries: List<VodProgressEntry>, labels: VodLabels, focus: ContextFocus? = null, onPlay: (VodProgressEntry) -> Unit
) {
    val list = rememberLazyListState()
    val keys = entries.map { "${it.kind}-${it.id}" }
    focus?.Restore("continued", keys, scrollTo = { list.scrollToItem(it) })
    focus?.Maintain("continued", keys)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(labels.resume(), style = MaterialTheme.typography.titleMedium)
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(entries, key = { _, it -> "${it.kind}-${it.id}" }) { index, entry ->
                var focused by remember { mutableStateOf(false) }
                Surface(onClick = { focus?.record("continued", keys[index], index); onPlay(entry) },
                    modifier = Modifier.width(210.dp).then(focus?.target("continued", keys[index], index) ?: Modifier)
                        .tvFocusIndicator().onFocusChanged { focused = it.isFocused },
                    color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                        val fraction = (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text(labels.position(entry.positionMs), color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}