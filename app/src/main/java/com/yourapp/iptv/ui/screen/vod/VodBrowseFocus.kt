package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodItem
import com.yourapp.iptv.ui.component.common.*
import com.yourapp.iptv.viewmodel.VodUiState

/** Horizontal category pills, with the same stable focus context and category IDs. */
@Composable
internal fun VodCategories(state: VodUiState, labels: VodLabels, focus: ContextFocus, posters: String,
    modifier: Modifier, onPick: (String?) -> Unit) {
    val list = rememberLazyListState()
    val choices = buildList {
        if (!state.catalog.categoryScoped) add(Triple("all", labels.allShort(), null))
        state.catalog.categories.forEach { add(Triple("category:${it.id}", it.name, it.id)) }
    }
    focus.Restore("categories", choices.map { it.first }, scrollTo = { list.scrollToItem(it) })
    LazyRow(state = list, modifier = modifier.onPreviewKeyEvent {
        if (it.key != Key.DirectionDown) false else {
            if (it.type == KeyEventType.KeyDown) focus.request(posters)
            true
        }
    }.testTag("vod-categories"), horizontalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(end = 12.dp)) {
        itemsIndexed(choices, key = { _, it -> it.first }) { index, choice ->
            var focused by remember { mutableStateOf(false) }
            val chosen = state.categoryId == choice.third
            val colors = MaterialTheme.colorScheme
            Surface(onClick = { focus.record("categories", choice.first, index); onPick(choice.third) },
                modifier = Modifier.then(focus.target("categories", choice.first, index))
                    .tvFocusIndicator(if (chosen) VodCutShape else RoundedCornerShape(25.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .testTag("vod-${choice.first}"),
                color = if (chosen) colors.primary else if (focused) colors.primaryContainer else colors.surfaceContainerLow,
                border = BorderStroke(if (focused) 2.dp else 1.dp,
                    if (focused) colors.primary else colors.outlineVariant.copy(alpha = .65f)),
                shape = if (chosen) VodCutShape else RoundedCornerShape(25.dp)) {
                Text(choice.second, Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (chosen) colors.onPrimary else colors.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun VodPosterGrid(items: List<VodItem>, focus: ContextFocus, context: String, ready: Boolean,
    labels: VodLabels, emptyCatalog: Boolean, modifier: Modifier,
    onFocused: (VodItem) -> Unit = {}, onPick: (Int, VodItem) -> Unit) {
    val list = rememberLazyGridState()
    val keys = remember(items) { items.map { it.id } }
    focus.Restore(context, keys, ready, onEmpty = { focus.request("controls", "query") },
        scrollTo = { list.scrollToItem(it) })
    if (items.isEmpty()) Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(if (emptyCatalog) labels.emptyCatalog() else labels.empty())
    } else LazyVerticalGrid(GridCells.Adaptive(128.dp), state = list,
        modifier = modifier.testTag("vod-grid"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            VodPosterCard(item, focus.target(context, item.id, index)
                .testTag("vod-poster-${item.id}"), onFocused = onFocused) { onPick(index, item) }
        }
    }
}