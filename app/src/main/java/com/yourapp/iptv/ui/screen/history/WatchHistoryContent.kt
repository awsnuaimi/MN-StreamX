package com.yourapp.iptv.ui.screen.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.*
import com.yourapp.iptv.ui.theme.NeonBlue

@Composable
internal fun WatchHistoryContent(state: WatchHistoryUiState, playingId: String?, onPlay: (String, String) -> Unit,
    onRemove: (String, String) -> Unit, onClear: (String) -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    val list = rememberLazyListState()
    val entryFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    var focusedTarget by remember(state.sourceKey) { mutableStateOf<String?>(null) }
    var initialized by remember(state.sourceKey) { mutableStateOf(false) }
    var afterRemoval by remember(state.sourceKey) { mutableStateOf<Int?>(null) }
    var confirmSource by remember(state.sourceKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.sourceKey, state.isLoading, state.isSaving, state.rows) {
        if (state.isLoading || state.isSaving || (initialized && afterRemoval == null)) return@LaunchedEffect
        initialized = true
        val index = (afterRemoval ?: 0).coerceIn(0, (state.rows.size - 1).coerceAtLeast(0))
        afterRemoval = null
        focusedTarget = state.rows.getOrNull(index)?.channel?.id
        if (focusedTarget != null) list.scrollToItem(index)
        withFrameNanos { }
        runCatching { if (focusedTarget == null) closeFocus.requestFocus() else entryFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.history_title), Modifier.weight(1f), color = NeonBlue,
                style = MaterialTheme.typography.headlineSmall)
            AppButton(stringResource(R.string.history_close), onBack, Modifier.focusRequester(closeFocus))
        }
        Text(stringResource(R.string.history_hint), style = MaterialTheme.typography.bodySmall)
        if (state.error != null) {
            Text(stringResource(when (state.error) {
                WatchHistoryError.READ -> R.string.history_read_error
                WatchHistoryError.CHANNELS -> R.string.history_channels_error
                WatchHistoryError.WRITE -> R.string.history_write_error
            }), color = MaterialTheme.colorScheme.error)
            AppButton(stringResource(R.string.history_retry), onRetry, enabled = !state.isSaving)
        }
        if (state.totalRecords > state.rows.size) Text(stringResource(R.string.history_missing,
            state.totalRecords - state.rows.size), style = MaterialTheme.typography.bodySmall)
        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.needsSource -> Text(stringResource(R.string.history_source), Modifier.weight(1f))
            state.rows.isEmpty() -> Text(stringResource(R.string.history_empty), Modifier.weight(1f))
            else -> LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.rows, key = { _, row -> row.channel.id }) { index, row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        var focused by remember { mutableStateOf(false) }
                        Surface(onClick = { onPlay(state.sourceKey, row.channel.id) }, enabled = !state.isSaving,
                            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                .then(if (row.channel.id == focusedTarget) Modifier.focusRequester(entryFocus) else Modifier)
                                .onFocusChanged { focused = it.isFocused },
                            color = if (focused) Color(0xFF164362) else Color(0xFF17243D),
                            shape = MaterialTheme.shapes.medium,
                            border = if (focused) BorderStroke(2.dp, NeonBlue) else null) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ChannelLogo(row.channel.name, row.channel.logoUrl, Modifier.size(42.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(row.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (row.channel.id == playingId) Text(stringResource(R.string.history_playing),
                                        color = NeonBlue, style = MaterialTheme.typography.bodySmall)
                                    else row.channel.group?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        AppButton(stringResource(R.string.history_remove), {
                            afterRemoval = index; onRemove(state.sourceKey, row.channel.id)
                        }, enabled = !state.isSaving)
                    }
                }
            }
        }
        AppButton(stringResource(if (state.isSaving) R.string.history_saving else R.string.history_clear),
            { confirmSource = state.sourceKey }, enabled = !state.isLoading && !state.isSaving && state.totalRecords > 0)
    }
    if (confirmSource != null) AlertDialog(onDismissRequest = { confirmSource = null },
        title = { Text(stringResource(R.string.history_clear)) },
        text = { Text(stringResource(R.string.history_clear_question)) },
        confirmButton = { AppButton(stringResource(R.string.history_clear), {
            val source = confirmSource; confirmSource = null
            if (source != null) { afterRemoval = 0; onClear(source) }
        }) },
        dismissButton = { AppButton(stringResource(R.string.history_cancel), { confirmSource = null }) })
}