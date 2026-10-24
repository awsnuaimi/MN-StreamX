package com.yourapp.iptv.ui.screen.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel
import com.yourapp.iptv.viewmodel.VodViewModel
import com.yourapp.iptv.viewmodel.VodProgressViewModel

@Composable
fun VodScreen(kind: VodKind, onBack: () -> Unit, onPlay: (VodKind, String, String, String) -> Unit,
    model: VodViewModel = hiltViewModel(), appearanceModel: HomeAppearanceViewModel = hiltViewModel(),
    progressModel: VodProgressViewModel = hiltViewModel()) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val progress by progressModel.entries.collectAsStateWithLifecycle()
    val appearance by appearanceModel.appearance.collectAsStateWithLifecycle()
    val language = appearance?.language ?: HomeLanguage.ARABIC
    val labels = remember(language) { VodLabels(language) }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(kind) { model.load(kind) }
    val visible = remember(state.catalog.items, state.categoryId, state.query) { state.visible }
    LaunchedEffect(state.loading) {
        if (!state.loading) { withFrameNanos { }; runCatching { backFocus.requestFocus() } }
    }
    BackHandler(state.selected != null) { model.close() }
    CompositionLocalProvider(LocalLayoutDirection provides if (language == HomeLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onBack, modifier = Modifier.focusRequester(backFocus)) { Text(labels.back()) }
                Text(labels.title(kind), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                if (!state.loading) Text("${visible.size} / ${state.catalog.items.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.unavailable -> VodMessage(labels.requiresXtream(), labels.back(), onBack)
                state.error -> VodMessage(labels.failed(), labels.tryAgain(), { model.load(kind, refresh = true) })
                else -> {
                    val continued = remember(progress, kind) { progress.filter { it.kind == kind }.take(8) }
                    if (continued.isNotEmpty()) {
                        VodContinueWatching(continued, labels) { entry ->
                            onPlay(entry.kind, entry.id, entry.extension, entry.title)
                        }
                    }
                    OutlinedTextField(state.query, model::search, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(labels.search()) })
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LazyColumn(Modifier.width(178.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            item { VodCategoryButton(labels.all(), state.categoryId == null) { model.filter(null) } }
                            items(state.catalog.categories, key = { it.id }) { category ->
                                VodCategoryButton(category.name, state.categoryId == category.id) { model.filter(category.id) }
                            }
                        }
                        if (visible.isEmpty()) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(labels.empty())
                        } else LazyVerticalGrid(GridCells.Adaptive(135.dp), modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(visible, key = { it.id }) { item -> VodPosterCard(item) { model.open(item) } }
                        }
                    }
                }
            }
        }
        state.selected?.let { selected ->
            VodDetailsDialog(state, selected, labels, progress, onDismiss = model::close,
                onRetry = { model.open(selected) }, onPlay = { chosenKind, id, ext, title ->
                model.close()
                onPlay(chosenKind, id, ext, title)
            })
        }
    }
}

@Composable
private fun VodCategoryButton(title: String, chosen: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        color = if (chosen || focused) Color(0xFF19334A) else Color(0xFF101B30),
        border = if (focused) BorderStroke(2.dp, Color.Cyan) else null,
        shape = MaterialTheme.shapes.small) {
        Text(title, Modifier.padding(12.dp), color = Color.White, maxLines = 3,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VodMessage(message: String, action: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message)
            Button(onClick = onClick) { Text(action) }
        }
    }
}