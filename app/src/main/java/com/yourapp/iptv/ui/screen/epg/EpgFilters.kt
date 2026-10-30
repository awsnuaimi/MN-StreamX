package com.yourapp.iptv.ui.screen.epg

import com.yourapp.iptv.ui.component.organization.FolderFilterButton
import com.yourapp.iptv.ui.component.organization.organizationFilterName
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.yourapp.iptv.R
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.ContextFocus
import com.yourapp.iptv.ui.state.EpgUiState

@Composable
internal fun EpgFilters(
    state: EpgUiState, focus: ContextFocus,
    onQuery: (String) -> Unit, onGroup: (String?) -> Unit, onFavorites: () -> Unit, onBack: () -> Unit,
    compact: Boolean = false
) {
    val controls = if (compact) listOf("back") else listOf("back", "query")
    val groups = remember(state.groups, state.organization.enabled) {
        listOf("@all", "@favorites") + if (state.organization.enabled) listOf("@folders")
            else state.groups.map { "group:$it" }
    }
    val list = rememberLazyListState()
    focus.Maintain(EPG_GROUPS, groups, ready = !state.isLoading && !compact)
    focus.Restore(EPG_FILTERS, controls, ready = !state.isLoading ||
        (focus.pending?.preferred ?: focus.bookmarks.get(EPG_FILTERS)?.key) == "back")
    if (!compact) focus.Restore(EPG_GROUPS, groups, ready = !state.isLoading,
        scrollTo = { list.scrollToItem(it) })
    else focus.Restore(EPG_GROUPS, emptyList(), onEmpty = { focus.request(EPG_FILTERS, "back") })
    Column(Modifier.fillMaxWidth().focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_epg_title), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(pluralStringResource(if (state.favoritesOnly) R.plurals.ui_epg_favorite_summary else R.plurals.ui_epg_summary,
                    state.queue.size, state.queue.size, organizationFilterName(state.selectedGroup, state.organization) ?: stringResource(R.string.player_all_categories)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            AppButton(stringResource(R.string.ui_back), onBack,
                focus.target(EPG_FILTERS, "back", 0).testTag("epg-back"))
        }
        if (!compact) OutlinedTextField(value = state.query, onValueChange = {
            focus.cancel(); onQuery(it)
        }, singleLine = true,
            enabled = !state.isLoading, label = { Text(stringResource(R.string.ui_epg_search)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().then(focus.target(EPG_FILTERS, "query", 1)).testTag("epg-query"))
        if (!compact) {
            LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item("@all") {
                    FilterChip(selected = state.selectedGroup == null && !state.favoritesOnly, onClick = {
                        focus.record(EPG_GROUPS, "@all", 0); onGroup(null)
                    }, modifier = focus.target(EPG_GROUPS, "@all", 0),
                        enabled = !state.isLoading, label = { Text(stringResource(R.string.ui_all)) })
                }
                item("@favorites") {
                    FilterChip(selected = state.favoritesOnly, onClick = {
                        focus.record(EPG_GROUPS, "@favorites", 1); onFavorites()
                    }, modifier = focus.target(EPG_GROUPS, "@favorites", 1),
                        enabled = !state.isLoading, label = { Text(stringResource(R.string.ui_favorites_star)) })
                }
                if (state.organization.enabled) item("@folders") {
                    Box(focus.target(EPG_GROUPS, "@folders", 2)) {
                        FolderFilterButton(state.organization, state.selectedGroup, onGroup, enabled = !state.isLoading)
                    }
                } else itemsIndexed(state.groups, key = { _, group -> "group:$group" }) { index, group ->
                    FilterChip(selected = state.selectedGroup == group, onClick = {
                        focus.record(EPG_GROUPS, "group:$group", index + 2); onGroup(group)
                    }, modifier = Modifier.widthIn(max = 260.dp).then(focus.target(EPG_GROUPS, "group:$group", index + 2)), enabled = !state.isLoading,
                        label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
    }
}