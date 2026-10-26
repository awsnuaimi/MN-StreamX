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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.ui.state.SearchVodState

internal data class SearchResult(val key: String, val title: String, val subtitle: String?, val image: String?)

@Composable
internal fun SearchResultStrip(results: List<SearchResult>, selectedKey: String?, restore: Int,
    onPick: (Int, String) -> Unit) {
    val list = rememberLazyListState()
    val restoreFocus = remember { FocusRequester() }
    LaunchedEffect(restore, selectedKey) {
        val index = results.indexOfFirst { it.key == selectedKey }
        if (restore > 0 && index >= 0) {
            list.scrollToItem(index)
            withFrameNanos { }
            runCatching { restoreFocus.requestFocus() }
        }
    }
    LazyRow(state = list, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(results, key = { _, item -> item.key }) { index, item ->
            var focused by remember(item.key) { mutableStateOf(false) }
            Surface(onClick = { onPick(index, item.key) },
                modifier = Modifier.width(276.dp).height(104.dp).testTag(item.key)
                    .then(if (item.key == selectedKey) Modifier.focusRequester(restoreFocus) else Modifier)
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
    selectedKey: String?, restore: Int, onPick: (VodItem, String) -> Unit,
    onRetry: () -> Unit, onScan: () -> Unit, onStop: () -> Unit) {
    when {
        state.unavailable -> Text(labels.unsupported())
        state.error -> {
            Text(labels.failed(), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text(labels.retry()) }
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
                SearchResultStrip(rows, selectedKey, restore) { index, key -> onPick(state.results[index], key) }
            } else if (!state.searching) Text(if (state.categoryScoped && state.categoriesSearched == 0) labels.notSearched() else labels.noResults())
            if (state.more) Text(labels.refine(), style = MaterialTheme.typography.bodySmall)
            if (state.scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = onStop, modifier = Modifier.testTag("$prefix-stop")) { Text(labels.stop()) }
            } else if (state.canScan) {
                OutlinedButton(onClick = onScan, modifier = Modifier.testTag("$prefix-scan")) { Text(labels.scan()) }
            }
        }
    }
}