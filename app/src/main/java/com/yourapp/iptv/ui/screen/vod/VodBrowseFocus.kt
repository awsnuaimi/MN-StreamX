package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.ui.component.common.*
import com.yourapp.iptv.viewmodel.VodUiState

@Composable
internal fun VodCategories(state: VodUiState, labels: VodLabels, focus: ContextFocus, posters: String,
    modifier: Modifier, onPick: (String?) -> Unit) {
    val list = rememberLazyListState()
    val choices = buildList {
        if (!state.catalog.categoryScoped) add(Triple("all", labels.all(), null))
        state.catalog.categories.forEach { add(Triple("category:${it.id}", it.name, it.id)) }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    focus.Restore("categories", choices.map { it.first }, scrollTo = { list.scrollToItem(it) })
    LazyColumn(state = list, modifier = modifier.onPreviewKeyEvent {
        val towardGrid = if (rtl) Key.DirectionLeft else Key.DirectionRight
        if (it.key != towardGrid) false else {
            if (it.type == KeyEventType.KeyDown) focus.request(posters)
            true
        }
    }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        itemsIndexed(choices, key = { _, it -> it.first }) { index, item ->
            var focused by remember { mutableStateOf(false) }
            val chosen = state.categoryId == item.third
            Surface(onClick = { focus.record("categories", item.first, index); onPick(item.third) },
                modifier = Modifier.fillMaxWidth().then(focus.target("categories", item.first, index))
                    .tvFocusIndicator().onFocusChanged { focused = it.isFocused }.testTag("vod-${item.first}"),
                color = if (chosen || focused) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                shape = MaterialTheme.shapes.small) {
                Text(item.second, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurface, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun VodPosterGrid(items: List<VodItem>, focus: ContextFocus, context: String, ready: Boolean,
    labels: VodLabels, emptyCatalog: Boolean, modifier: Modifier, onPick: (Int, VodItem) -> Unit) {
    val list = rememberLazyGridState()
    val keys = remember(items) { items.map { it.id } }
    focus.Restore(context, keys, ready, onEmpty = { focus.request("controls", "query") },
        scrollTo = { list.scrollToItem(it) })
    if (items.isEmpty()) Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(if (emptyCatalog) labels.emptyCatalog() else labels.empty())
    } else LazyVerticalGrid(GridCells.Adaptive(135.dp), state = list, modifier = modifier.testTag("vod-grid"),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            VodPosterCard(item, focus.target(context, item.id, index).testTag("vod-poster-${item.id}")) { onPick(index, item) }
        }
    }
}