package com.yourapp.iptv.ui.screen.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.delay

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
    // Sort is local UI state; catalog and playback models are never reordered or mutated.
    var nameSort by rememberSaveable(state.kind, state.sourceKey ?: lastSource) { mutableStateOf(false) }
    val original = remember(state.catalog.items, state.categoryId, state.query, state.filteredItems) { state.visible }
    val visible = remember(original, nameSort) {
        if (nameSort) original.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) else original
    }
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
    val ready = !state.loading && !state.categoryLoading && !state.filtering
    focus.Maintain(posters, posterKeys, ready)
    var hadDialog by remember(focus) { mutableStateOf(false) }
    LaunchedEffect(state.selected, focus) {
        if (state.selected != null) { focus.cancel(); hadDialog = true }
        else if (hadDialog) { hadDialog = false; focus.restore(contexts, "controls", "query") }
    }
    BackHandler(state.selected != null, actions.close)
    // Focused artwork is local and keyed by catalog scope; it cannot bleed between profiles.
    var highlighted by remember(state.kind, state.sourceKey ?: lastSource, state.categoryId, state.query) {
        mutableStateOf<String?>(null)
    }
    var hoverCandidate by remember(state.kind, state.sourceKey ?: lastSource,
        state.categoryId, state.query) { mutableStateOf<String?>(null) }
    LaunchedEffect(hoverCandidate) {
        if (hoverCandidate != null) {
            delay(180)
            highlighted = hoverCandidate
        }
    }
    val featured = visible.firstOrNull { it.id == highlighted } ?: visible.firstOrNull()
    val continued = remember(progress, state.kind) { progress.filter { it.kind == state.kind }.take(8) }
    val rtl = labels.language == HomeLanguage.ARABIC
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Row(Modifier.fillMaxSize().then(focus.inputModifier()).testTag("vod-page")) {
            VodCatalogRail(state.kind, actions.back)
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 20.dp,
                top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                VodCatalogToolbar(state.kind, visible.size, state.query, labels, focus,
                    state.loading, !state.loading && !state.unavailable && !state.error,
                    actions.back, actions.refresh,
                    { focus.cancel(); actions.search(it) },
                    { focus.request("categories") }, nameSort, { nameSort = !nameSort })
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.unavailable -> VodMessage(labels.requiresXtream(), labels.back(), actions.back,
                        focus.target("controls", "message", 3))
                    state.error -> VodMessage(labels.failed(), labels.tryAgain(), actions.refresh,
                        focus.target("controls", "message", 3))
                    else -> {
                        if (state.catalog.categoryScoped) Text(labels.categoryMode(),
                            style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        if (state.catalog.categoriesUnavailable) Text(labels.categoriesUnavailable(),
                            style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        VodCategories(state, labels, focus, posters, Modifier.fillMaxWidth().height(44.dp)) { id ->
                            focus.request(vodPosterContext(id, state.query)); actions.category(id)
                        }
                        Row(Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            when {
                                state.categoryLoading -> Box(Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                state.categoryError -> {
                                    focus.Restore(posters, emptyList(), onEmpty = { focus.request("category-status") })
                                    focus.Restore("category-status", listOf("retry"))
                                    Box(Modifier.weight(1f).fillMaxHeight()) {
                                        VodMessage(labels.failed(), labels.tryAgain(), {
                                            focus.record("category-status", "retry", 0)
                                            focus.request(posters); actions.retryCategory()
                                        }, focus.target("category-status", "retry", 0).testTag("vod-category-retry"))
                                    }
                                }
                                state.filtering -> Box(Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                else -> VodPosterGrid(visible, focus, posters, state.selected == null,
                                    labels, state.catalog.items.isEmpty() && state.query.isBlank(),
                                    Modifier.weight(1f).fillMaxHeight(),
                                    onFocused = { hoverCandidate = it.id },
                                    header = {
                                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            if (state.kind == VodKind.MOVIE && featured != null) {
                                                VodMovieHero(featured, labels, onOpen = { actions.open(featured) })
                                            }
                                            if (continued.isNotEmpty()) {
                                                Box(Modifier.onPreviewKeyEvent { event ->
                                                    if (event.key != Key.DirectionDown) false else {
                                                        if (event.type == KeyEventType.KeyDown) focus.request(posters)
                                                        true
                                                    }
                                                }) {
                                                    VodContinueWatching(continued, labels, focus, visible) { entry ->
                                                        actions.play(entry.kind, entry.id, entry.extension,
                                                            entry.title, entry.seriesId)
                                                    }
                                                }
                                            }
                                            Text(labels.newTitles(), style = MaterialTheme.typography.titleMedium)
                                        }
                                    }) { index, item ->
                                    focus.record(posters, item.id, index); actions.open(item)
                                }
                            }
                            if (state.kind == VodKind.SERIES) {
                                VodSeriesPreview(featured, progress, labels, actions.open,
                                    Modifier.width(250.dp).fillMaxHeight())
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