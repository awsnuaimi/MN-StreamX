package com.yourapp.iptv.ui.screen.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.search.SearchText
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.screen.vod.VodDetailsDialog
import com.yourapp.iptv.ui.screen.vod.VodLabels
import com.yourapp.iptv.ui.state.SearchUiState
import com.yourapp.iptv.viewmodel.*
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(onBack: () -> Unit, onSettings: () -> Unit, onOpenLive: () -> Unit,
    onPlayChannel: (String, String) -> Unit, onPlayVod: (VodKind, String, String, String, String) -> Unit,
    model: SearchViewModel = hiltViewModel(), appearanceModel: HomeAppearanceViewModel = hiltViewModel(),
    progressModel: VodProgressViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val details by model.details.collectAsStateWithLifecycle()
    val progress by progressModel.entries.collectAsStateWithLifecycle()
    val appearance by appearanceModel.appearance.collectAsStateWithLifecycle()
    val language = appearance?.language ?: HomeLanguage.ARABIC
    val labels = remember(language) { SearchLabels(language) }
    val vodLabels = remember(language) { VodLabels(language) }
    val owner = LocalLifecycleOwner.current
    var restore by remember { mutableIntStateOf(0) }
    DisposableEffect(model, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.setVisible(true)
                Lifecycle.Event.ON_STOP -> model.setVisible(false)
                else -> Unit
            }
        }
        model.setVisible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.setVisible(false) }
    }
    fun close() { model.closeDetails(); restore++ }
    BackHandler(details.selected != null) { close() }
    LocalizedResources(language) {
        val source = state.sourceKey
        SearchContent(state, labels, SearchActions(
            onBack = onBack, onSettings = onSettings, onOpenLive = onOpenLive,
            onQuery = model::updateQuery, onSubmit = model::submit,
            onHistory = { if (model.state.value.sourceKey == source) model.useHistory(it) },
            onClearHistory = model::clearHistory, onRetrySource = model::retrySource,
            onReloadChannels = model::reloadChannels, onRetry = model::retry,
            onScan = model::scanMore, onStop = model::stopScan,
            onChannel = { channel ->
                if (source != null && model.chooseChannel(channel, source)) onPlayChannel(channel.id, source)
            },
            onVod = { kind, item -> if (source != null) model.open(kind, item, source) }
        ), restore)
        details.selected?.let { chosen ->
            VodDetailsDialog(details, chosen, vodLabels, progress, ::close, model::retryDetails) { kind, id, ext, title ->
                val expected = source
                if (expected != null && model.state.value.sourceKey == expected) {
                    close()
                    onPlayVod(kind, id, ext, title, expected)
                }
            }
        }
    }
}

internal data class SearchActions(
    val onBack: () -> Unit, val onSettings: () -> Unit, val onOpenLive: () -> Unit,
    val onQuery: (String) -> Unit, val onSubmit: () -> Unit, val onHistory: (String) -> Unit,
    val onClearHistory: () -> Unit, val onRetrySource: () -> Unit, val onReloadChannels: () -> Unit,
    val onRetry: (VodKind) -> Unit, val onScan: (VodKind) -> Unit, val onStop: (VodKind) -> Unit,
    val onChannel: (Channel) -> Unit, val onVod: (VodKind, VodItem) -> Unit
)

@Composable
internal fun SearchContent(state: SearchUiState, labels: SearchLabels, actions: SearchActions, restoreRequest: Int = 0) {
    val queryFocus = remember { FocusRequester() }
    val resultsFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedKey by rememberSaveable(state.sourceKey) { mutableStateOf<String?>(null) }
    var restore by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.sourceKey, restoreRequest) {
        val selected = selectedKey
        if (selected == null) {
            withFrameNanos { }
            runCatching { queryFocus.requestFocus() }
        } else {
            val section = when { selected.startsWith("movie-") -> 1; selected.startsWith("series-") -> 2; else -> 0 }
            if (list.layoutInfo.totalItemsCount > section) list.scrollToItem(section)
            withFrameNanos { }
            restore++
        }
    }
    fun submit() { keyboard?.hide(); actions.onSubmit() }
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("search-page"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = actions.onBack) { Text(labels.back()) }
            Text(labels.title(), style = MaterialTheme.typography.headlineSmall)
        }
        Text(labels.scope(), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(state.query, {
                selectedKey = null
                actions.onQuery(it)
            }, modifier = Modifier.weight(1f).focusRequester(queryFocus).testTag("search-query")
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                        keyboard?.hide()
                        scope.launch {
                            if (list.layoutInfo.totalItemsCount > 0) list.scrollToItem(0)
                            withFrameNanos { }
                            runCatching { resultsFocus.requestFocus() }
                        }
                        true
                    } else false
                },
                label = { Text(labels.field()) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }))
            Button(onClick = ::submit, enabled = state.sourceKey != null && SearchText.normalized(state.query).isNotBlank(),
                modifier = Modifier.testTag("search-submit")) { Text(labels.search()) }
            if (state.query.isNotEmpty()) TextButton(onClick = { selectedKey = null; actions.onQuery("") }) { Text(labels.clearQuery()) }
        }
        when {
            state.sourceLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            state.sourceError -> {
                Text(labels.sourceError(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = actions.onRetrySource) { Text(labels.retry()) }
            }
            state.needsSource -> {
                Text(labels.needsSource())
                OutlinedButton(onClick = actions.onSettings) { Text(labels.settings()) }
            }
        }
        if (state.historyError) {
            Text(labels.historyError(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = actions.onClearHistory) { Text(labels.clearHistory()) }
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()
            .focusRequester(resultsFocus).focusGroup().testTag("search-results"),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (SearchText.normalized(state.query).isBlank()) {
                item(key = "start") { Text(labels.start()) }
                if (state.history.isNotEmpty()) {
                    item(key = "recent") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(labels.recent(), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = actions.onClearHistory, modifier = Modifier.testTag("search-clear-history")) { Text(labels.clearHistory()) }
                        }
                    }
                    items(state.history, key = { it }) { query ->
                        OutlinedButton(onClick = { selectedKey = null; actions.onHistory(query) }, modifier = Modifier.fillMaxWidth()) { Text(query) }
                    }
                }
            } else if (state.sourceKey != null) {
                item(key = "channels") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${labels.channels()} (${state.channels.size})", style = MaterialTheme.typography.titleMedium)
                        when {
                            state.channelsLoading || state.channelsSearching -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.channelsError -> {
                                Text(labels.failed(), color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = actions.onReloadChannels) { Text(labels.retry()) }
                            }
                            state.channelsMissing -> {
                                Text(labels.missingChannels())
                                OutlinedButton(onClick = actions.onOpenLive) { Text(labels.live()) }
                            }
                            state.channels.isEmpty() -> Text(labels.noResults())
                            else -> {
                                val rows = remember(state.channels) { state.channels.map {
                                    SearchResult("channel-${it.id}", it.name, it.group, it.logoUrl)
                                } }
                                SearchResultStrip(rows, selectedKey, restore) { index, key ->
                                    selectedKey = key; actions.onChannel(state.channels[index])
                                }
                            }
                        }
                        if (state.channelsMore) Text(labels.refine(), style = MaterialTheme.typography.bodySmall)
                        if (state.channelsLimited) Text(labels.indexLimited(), color = MaterialTheme.colorScheme.error)
                    }
                }
                for (kind in VodKind.entries) item(key = kind.name) {
                    val lane = if (kind == VodKind.MOVIE) state.movies else state.series
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${labels.kind(kind)} (${lane.results.size})", style = MaterialTheme.typography.titleMedium)
                        SearchVodResults(lane, labels, if (kind == VodKind.MOVIE) "movie" else "series", selectedKey, restore,
                            onPick = { item, key -> selectedKey = key; actions.onVod(kind, item) },
                            onRetry = { actions.onRetry(kind) }, onScan = { actions.onScan(kind) }, onStop = { actions.onStop(kind) })
                    }
                }
            }
        }
    }
}