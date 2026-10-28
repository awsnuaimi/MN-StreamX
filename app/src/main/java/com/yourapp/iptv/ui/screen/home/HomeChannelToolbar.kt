package com.yourapp.iptv.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.HomeChannelLayout

/** Search occupies a row only while open. Closing it also removes the filter. */
@Composable
internal fun HomeChannelToolbar(title: String, visible: Int, total: Int, labels: HomeLabels,
    query: String, onQuery: (String) -> Unit, layout: HomeChannelLayout,
    onLayout: (HomeChannelLayout) -> Unit, modifier: Modifier = Modifier,
    saving: Boolean = false, saveError: Boolean = false) {
    var searchOpen by rememberSaveable { mutableStateOf(query.isNotBlank()) }
    val searchVisible = searchOpen || query.isNotBlank()
    val searchButton = remember { FocusRequester() }
    val searchField = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var returnToSearch by remember { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    fun closeSearch() {
        searchOpen = false; focusSearch = false; onQuery(""); keyboard?.hide(); returnToSearch = true
    }
    BackHandler(enabled = searchVisible) { closeSearch() }
    LaunchedEffect(searchVisible, returnToSearch, focusSearch) {
        if (returnToSearch && !searchVisible) {
            withFrameNanos { }
            searchButton.requestFocus()
            returnToSearch = false
        } else if (searchVisible && focusSearch && !returnToSearch) {
            withFrameNanos { }; searchField.requestFocus(); focusSearch = false
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                Text(labels.text("$visible من $total قناة", "$visible of $total channels", "$visible von $total Sendern"),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BrowseToolbarButton(
                labels.text(if (searchVisible) "إغلاق البحث" else "البحث عن قناة",
                    if (searchVisible) "Close search" else "Search channels",
                    if (searchVisible) "Suche schließen" else "Sender suchen"),
                searchVisible, Modifier.focusRequester(searchButton).testTag("browse-search-toggle"),
                onClick = {
                    if (searchVisible) closeSearch()
                    else { searchOpen = true; focusSearch = true }
                }) {
                HomeSidebarIcon(HomeSection.SEARCH, MaterialTheme.colorScheme.primary)
            }
            val next = if (layout == HomeChannelLayout.GRID) HomeChannelLayout.LIST else HomeChannelLayout.GRID
            BrowseToolbarButton(
                labels.text(if (next == HomeChannelLayout.LIST) "عرض قائمة" else "عرض شبكة",
                    if (next == HomeChannelLayout.LIST) "Show list" else "Show grid",
                    if (next == HomeChannelLayout.LIST) "Listenansicht" else "Rasteransicht"),
                layout == HomeChannelLayout.LIST, Modifier.testTag("browse-layout-toggle"),
                onClick = { if (!saving) onLayout(next) }) {
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.size(24.dp)) {
                    if (next == HomeChannelLayout.LIST) repeat(3) { row ->
                        drawLine(color, Offset(0f, size.height * (row + 0.5f) / 3f),
                            Offset(size.width, size.height * (row + 0.5f) / 3f), 2.dp.toPx())
                    } else repeat(2) { row -> repeat(2) { column ->
                        drawRect(color, Offset(column * size.width / 2f, row * size.height / 2f),
                            Size(size.width * 0.4f, size.height * 0.4f), style = Stroke(1.5.dp.toPx()))
                    } }
                }
            }
        }
        if (searchVisible) OutlinedTextField(query, onQuery,
            label = { Text(labels.text("البحث عن قناة", "Search channels", "Sender suchen")) },
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().focusRequester(searchField).testTag("browse-search-field"))
        if (saveError) Text(labels.text("تعذّر حفظ العرض. أعد المحاولة.",
            "Could not save the view. Try again.", "Ansicht konnte nicht gespeichert werden. Erneut versuchen."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun BrowseToolbarButton(description: String, selected: Boolean, modifier: Modifier,
    onClick: () -> Unit, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // Keep the focus target mounted and enabled while an accepted save finishes.
    Surface(onClick = onClick, modifier = modifier.size(48.dp).onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = description; this.selected = selected },
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}