package com.yourapp.iptv.ui.screen.epg

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
    onQuery: (String) -> Unit, onGroup: (String?) -> Unit, onBack: () -> Unit, onFocused: () -> Unit,
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
                Text("دليل البرامج", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
                Text("${state.queue.size} قناة · ${state.selectedGroup ?: "كل التصنيفات"}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            AppButton("رجوع", onBack)
        }
        if (!compact) OutlinedTextField(value = state.query, onValueChange = onQuery, singleLine = true,
            enabled = !state.isLoading, label = { Text("البحث عن قناة في الدليل") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus))
        if (!compact && state.groups.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = state.selectedGroup == null, onClick = { onGroup(null) },
                        enabled = !state.isLoading, label = { Text("الكل") })
                }
                items(state.groups, key = { it }) { group ->
                    FilterChip(selected = state.selectedGroup == group, onClick = { onGroup(group) },
                        modifier = Modifier.widthIn(max = 260.dp), enabled = !state.isLoading,
                        label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
    }
}