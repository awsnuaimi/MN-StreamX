@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.yourapp.iptv.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourapp.iptv.ui.presentation.LiveChannelSort

@Composable
internal fun LiveTvHeader(title: String, visible: Int, total: Int, query: String, onQuery: (String) -> Unit,
    sort: LiveChannelSort, onSort: (LiveChannelSort) -> Unit, labels: HomeLabels, colors: HomeTvPalette,
    categories: FocusRequester? = null, entry: FocusRequester? = null, onManage: (() -> Unit)? = null, canSort: Boolean = true,
    sidebar: FocusRequester? = null, sidebarOnRight: Boolean = false, above: FocusRequester? = null,
    manageEntry: FocusRequester? = null) {
    var search by rememberSaveable { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    var requestField by remember { mutableStateOf(false) }
    var returnFocus by remember { mutableStateOf(false) }
    val defaultButton = remember { FocusRequester() }; val button = entry ?: defaultButton
    val field = remember { FocusRequester() }
    val sortButton = remember { FocusRequester() }
    val hiddenDefault = remember { FocusRequester() }; val hidden = manageEntry ?: hiddenDefault
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val keyboard = LocalSoftwareKeyboardController.current
    val shown = search || query.isNotEmpty()
    fun close() { search = false; onQuery(""); keyboard?.hide(); returnFocus = true }
    BackHandler(shown) { close() }
    LaunchedEffect(shown, requestField, returnFocus) {
        if (shown && requestField) { withFrameNanos { }; field.requestFocus(); requestField = false }
        if (!shown && returnFocus) { withFrameNanos { }; button.requestFocus(); returnFocus = false }
    }
    var hiddenFocused by remember { mutableStateOf(false) }
    var previousManage by remember { mutableStateOf(onManage != null) }
    val restoreManage = previousManage && onManage == null && hiddenFocused
    SideEffect { previousManage = onManage != null; if (restoreManage) button.requestFocus() }
    val buttons = listOfNotNull(button, hidden.takeIf { onManage != null }, sortButton)
    fun links(index: Int): Modifier {
        fun horizontal(direction: HomeDpad): FocusRequester? =
            HomeDpadPolicy.neighbor(index, buttons.size, direction, false, rtl)?.let(buttons::get)
                ?: sidebar.takeIf { HomeDpadPolicy.towardSidebar(direction, sidebarOnRight) }
        return Modifier.homeFocusLinks(up = above, down = if (shown) field else categories,
            left = horizontal(HomeDpad.LEFT), right = horizontal(HomeDpad.RIGHT))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 560.dp || LocalDensity.current.fontScale > 1.4f
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(title, color = colors.text, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!compact) LiveCount(visible, total, labels, colors)
                    }
                    if (compact) LiveCount(visible, total, labels, colors)
                }
                LiveTvPill(labels.text("بحث", "Search", "Suchen"), colors,
                    { if (shown) close() else { search = true; requestField = true } },
                    Modifier.focusRequester(button).then(links(0)).testTag("live-search"))
                if (onManage != null) LiveTvPill(labels.text("المخفي", "Hidden", "Ausgeblendet"), colors, onManage,
                    Modifier.focusRequester(hidden).onFocusChanged { hiddenFocused = it.isFocused }.then(links(1)).testTag("live-hidden"))
                Box {
                    LiveTvPill(labels.text("الترتيب", "Sort", "Sortieren"), colors, { if (canSort) sorting = true },
                        Modifier.focusRequester(sortButton).then(links(buttons.lastIndex)).testTag("live-sort"))
                    DropdownMenu(sorting, { sorting = false; sortButton.requestFocus() }) {
                        LiveChannelSort.entries.forEach { value ->
                            DropdownMenuItem(text = { Text((if (sort == value) "✓  " else "") + when (value) {
                                LiveChannelSort.PROVIDER -> labels.text("ترتيب المزوّد", "Provider order", "Senderreihenfolge")
                                LiveChannelSort.NAME -> labels.text("أبجديًا", "Alphabetical", "Alphabetisch")
                                LiveChannelSort.FAVORITES -> labels.text("المفضلة أولًا", "Favorites first", "Favoriten zuerst")
                            }) }, onClick = { sorting = false; onSort(value); sortButton.requestFocus() },
                                modifier = Modifier.testTag("live-sort-${value.name}"))
                        }
                    }
                }
            }
        }
        if (shown) OutlinedTextField(query, onQuery, label = { Text(labels.text("بحث عن قناة", "Search channels", "Sender suchen")) },
            singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(field).homeDpadKeys(verticalOnly = true) {
                if (it == HomeDpad.UP) button.requestFocus() else categories?.requestFocus()
            }.focusProperties {
                up = button; down = categories ?: FocusRequester.Cancel
            }.testTag("live-search-field"),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
    }
}

@Composable
private fun LiveCount(visible: Int, total: Int, labels: HomeLabels, colors: HomeTvPalette) {
    Text(labels.text("$visible من $total قناة", "$visible of $total channels", "$visible von $total Sendern"),
        color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 3.dp).testTag("live-channel-count"))
}