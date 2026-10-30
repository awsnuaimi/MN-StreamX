package com.yourapp.iptv.ui.screen.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yourapp.iptv.ui.component.common.*
import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.ui.state.SearchVodState

internal data class SearchResult(val key: String, val title: String, val subtitle: String?, val image: String?)

@Composable
internal fun SearchResultStrip(results: List<SearchResult>, focus: ContextFocus, context: String,
    onPick: (Int, String) -> Unit) {
    val list = rememberLazyListState()
    val keys = remember(results) { results.map { it.key } }
    focus.Restore(context, keys, onEmpty = { focus.request("controls", "query") }, scrollTo = { list.scrollToItem(it) })
    LazyRow(state = list, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(results, key = { _, item -> item.key }) { index, item ->
            var focused by remember(item.key) { mutableStateOf(false) }
            Surface(onClick = { focus.record(context, item.key, index); onPick(index, item.key) },
                modifier = Modifier.width(276.dp).height(104.dp).testTag(item.key)
                    .then(focus.target(context, item.key, index)).tvFocusIndicator()
                    .onFocusChanged { focused = it.isFocused },
                color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.image?.let { it.startsWith("https://") || it.startsWith("http://") } == true) {
                        AsyncImage(item.image, contentDescription = null, modifier = Modifier.width(64.dp).fillMaxHeight(),
                            contentScale = ContentScale.Fit)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        item.subtitle?.takeIf(String::isNotBlank)?.let {
                            Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchVodResults(state: SearchVodState, labels: SearchLabels, prefix: String,
    focus: ContextFocus, context: String, onPick: (VodItem, String) -> Unit,
    onRetry: () -> Unit, onScan: () -> Unit, onStop: () -> Unit) {
    val actionContext = "$context-actions"
    val actionKeys = when {
        state.error -> listOf("retry")
        state.scanning -> listOf("stop")
        state.canScan -> listOf("scan")
        else -> emptyList()
    }
    focus.Restore(actionContext, actionKeys, onEmpty = { focus.request("controls", "query") })
    focus.Maintain(actionContext, actionKeys)
    if (!state.loading && state.loaded && !state.searching && state.results.isEmpty())
        focus.Restore(context, emptyList(), onEmpty = { focus.request("controls", "query") })
    when {
        state.unavailable -> Text(labels.unsupported())
        state.error -> {
            Text(labels.failed(), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry, modifier = focus.target(actionContext, "retry", 0).tvFocusIndicator()) { Text(labels.retry()) }
        }
        state.loading || !state.loaded -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(labels.loading())
        }
        else -> {
            Text(if (state.categoryScoped) labels.categoryScope(state.categoriesSearched, state.categoriesTotal)
                else if (state.indexLimited) labels.indexLimited() else labels.full(),
                style = MaterialTheme.typography.bodySmall)
            if (state.categoryScoped && state.indexLimited) Text(labels.indexLimited(), color = MaterialTheme.colorScheme.error)
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.categoriesFailed > 0) Text(labels.failedCategories(state.categoriesFailed), color = MaterialTheme.colorScheme.error)
            if (state.results.isNotEmpty()) {
                val rows = remember(state.results) { state.results.map { SearchResult("$prefix-${it.id}", it.name, it.year, it.poster) } }
                SearchResultStrip(rows, focus, context) { index, key -> onPick(state.results[index], key) }
            } else if (!state.searching) Text(if (state.categoryScoped && state.categoriesSearched == 0) labels.notSearched() else labels.noResults())
            if (state.more) Text(labels.refine(), style = MaterialTheme.typography.bodySmall)
            if (state.scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = onStop, modifier = focus.target(actionContext, "stop", 0).tvFocusIndicator().testTag("$prefix-stop")) { Text(labels.stop()) }
            } else if (state.canScan) {
                OutlinedButton(onClick = onScan, modifier = focus.target(actionContext, "scan", 0).tvFocusIndicator().testTag("$prefix-scan")) { Text(labels.scan()) }
            }
        }
    }
}