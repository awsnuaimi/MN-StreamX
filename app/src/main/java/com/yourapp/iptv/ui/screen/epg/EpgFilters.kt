package com.yourapp.iptv.ui.screen.epg

import com.yourapp.iptv.ui.component.organization.FolderFilterButton
import com.yourapp.iptv.ui.component.organization.organizationFilterName
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.yourapp.iptv.R
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.EpgUiState

@Composable
fun EpgFilters(
    state: EpgUiState, request: EpgFocusRequest, searchFocus: FocusRequester,
    onQuery: (String) -> Unit, onGroup: (String?) -> Unit, onFavorites: () -> Unit, onBack: () -> Unit, onFocused: () -> Unit,
    compact: Boolean = false
) {
    LaunchedEffect(request.serial) {
        if (request.serial != 0 && request.area == EpgFocusArea.FILTERS && !state.isLoading) {
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        }
    }
    Column(Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocused() }.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui_epg_title), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text(pluralStringResource(if (state.favoritesOnly) R.plurals.ui_epg_favorite_summary else R.plurals.ui_epg_summary,
                    state.queue.size, state.queue.size, organizationFilterName(state.selectedGroup, state.organization) ?: stringResource(R.string.player_all_categories)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            AppButton(stringResource(R.string.ui_back), onBack)
        }
        if (!compact) OutlinedTextField(value = state.query, onValueChange = onQuery, singleLine = true,
            enabled = !state.isLoading, label = { Text(stringResource(R.string.ui_epg_search)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus))
        if (!compact) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = state.selectedGroup == null && !state.favoritesOnly, onClick = { onGroup(null) },
                        enabled = !state.isLoading, label = { Text(stringResource(R.string.ui_all)) })
                }
                item {
                    FilterChip(selected = state.favoritesOnly, onClick = onFavorites,
                        enabled = !state.isLoading, label = { Text(stringResource(R.string.ui_favorites_star)) })
                }
                if (state.organization.enabled) item("@folders") {
                    FolderFilterButton(state.organization, state.selectedGroup, onGroup, enabled = !state.isLoading)
                } else items(state.groups, key = { it }) { group ->
                    FilterChip(selected = state.selectedGroup == group, onClick = { onGroup(group) },
                        modifier = Modifier.widthIn(max = 260.dp), enabled = !state.isLoading,
                        label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
    }
}