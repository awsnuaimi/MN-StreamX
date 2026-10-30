package com.yourapp.iptv.ui.screen.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.*
import com.yourapp.iptv.viewmodel.VodUiState

internal data class VodActions(val back: () -> Unit, val refresh: () -> Unit, val search: (String) -> Unit,
    val category: (String?) -> Unit, val retryCategory: () -> Unit, val open: (VodItem) -> Unit,
    val close: () -> Unit, val retryDetails: () -> Unit,
    val play: (VodKind, String, String, String, String?) -> Unit)

internal fun vodPosterContext(category: String?, query: String) = "posters:${category?.length ?: -1}:$category:$query"

@Composable
internal fun VodContent(state: VodUiState, progress: List<VodProgressEntry>, labels: VodLabels, actions: VodActions) {
    var lastSource by rememberSaveable(state.kind) { mutableStateOf<String?>(null) }
    val focus = rememberContextFocus("vod:${state.kind}:${state.sourceKey ?: lastSource}")
    SideEffect { if (state.sourceKey != null) lastSource = state.sourceKey }
    val visible = remember(state.catalog.items, state.categoryId, state.query) { state.visible }
    val posters = vodPosterContext(state.categoryId, state.query)
    val posterKeys = remember(visible) { visible.map { it.id } }
    val contexts = setOf("controls", "categories", posters, "continued", "category-status")
    focus.Resume(contexts, "controls", "back")
    val controls = buildList {
        add("back")
        if (!state.loading) add("refresh")
        if (!state.loading && !state.unavailable && !state.error) add("query")
        if (!state.loading && (state.error || state.unavailable)) add("message")
    }
    focus.Restore("controls", controls, ready = !state.loading ||
        (focus.pending?.preferred ?: focus.bookmarks.get("controls")?.key ?: "back") == "back")
    focus.Maintain("controls", controls)
    val ready = !state.loading && !state.categoryLoading
    focus.Maintain(posters, posterKeys, ready)
    var hadDialog by remember(focus) { mutableStateOf(false) }
    LaunchedEffect(state.selected, focus) {
        if (state.selected != null) { focus.cancel(); hadDialog = true }
        else if (hadDialog) { hadDialog = false; focus.restore(contexts, "controls", "query") }
    }
    BackHandler(state.selected != null, actions.close)
    val rtl = labels.language == HomeLanguage.ARABIC
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize().then(focus.inputModifier()).padding(16.dp).testTag("vod-page"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(actions.back, Modifier.then(focus.target("controls", "back", 0)).tvFocusIndicator().testTag("vod-back")) { Text(labels.back()) }
                Text(labels.title(state.kind), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                if (!state.loading) Button(actions.refresh, Modifier.then(focus.target("controls", "refresh", 1))
                    .tvFocusIndicator().testTag("vod-refresh")) { Text(labels.refresh()) }
                if (!state.loading) Text("${visible.size} / ${state.catalog.items.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.unavailable -> VodMessage(labels.requiresXtream(), labels.back(), actions.back,
                    focus.target("controls", "message", 3))
                state.error -> VodMessage(labels.failed(), labels.tryAgain(), actions.refresh,
                    focus.target("controls", "message", 3))
                else -> {
                    if (state.catalog.categoryScoped) Text(labels.categoryMode(), style = MaterialTheme.typography.bodySmall)
                    if (state.catalog.categoriesUnavailable) Text(labels.categoriesUnavailable(), style = MaterialTheme.typography.bodySmall)
                    val continued = remember(progress, state.kind) { progress.filter { it.kind == state.kind }.take(8) }
                    if (continued.isNotEmpty()) VodContinueWatching(continued, labels, focus) { entry ->
                        actions.play(entry.kind, entry.id, entry.extension, entry.title, entry.seriesId)
                    }
                    OutlinedTextField(state.query, { focus.cancel(); actions.search(it) }, Modifier.fillMaxWidth().then(focus.target("controls", "query", 2))
                        .testTag("vod-query"), singleLine = true, label = { Text(labels.search()) })
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VodCategories(state, labels, focus, posters, Modifier.width(178.dp).fillMaxHeight()) { id ->
                            focus.request(vodPosterContext(id, state.query))
                            actions.category(id)
                        }
                        when {
                            state.categoryLoading -> Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            state.categoryError -> {
                                focus.Restore(posters, emptyList(), onEmpty = { focus.request("category-status") })
                                focus.Restore("category-status", listOf("retry"))
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    VodMessage(labels.failed(), labels.tryAgain(), {
                                        focus.record("category-status", "retry", 0); focus.request(posters); actions.retryCategory()
                                    }, focus.target("category-status", "retry", 0).testTag("vod-category-retry"))
                                }
                            }
                            else -> VodPosterGrid(visible, focus, posters, state.selected == null,
                                labels, state.catalog.items.isEmpty() && state.query.isBlank(),
                                Modifier.weight(1f).fillMaxHeight()) { index, item ->
                                    focus.record(posters, item.id, index); actions.open(item)
                                }
                        }
                    }
                }
            }
        }
        state.selected?.let { selected ->
            VodDetailsDialog(state, selected, labels, progress, actions.close, actions.retryDetails, actions.play)
        }
    }
}

@Composable
internal fun VodMessage(message: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message)
            Button(onClick, modifier.tvFocusIndicator()) { Text(action) }
        }
    }
}