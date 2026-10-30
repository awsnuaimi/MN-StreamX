@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.yourapp.iptv.ui.screen.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.component.channel.ChannelLogo
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.FocusBookmarks
import com.yourapp.iptv.ui.component.common.Resume
import com.yourapp.iptv.ui.component.common.rememberContextFocus
import com.yourapp.iptv.ui.state.*

@Composable
internal fun WatchHistoryContent(state: WatchHistoryUiState, playingId: String?, onPlay: (String, String) -> Unit,
    onRemove: (String, String) -> Unit, onClear: (String) -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    val list = rememberLazyListState()
    val focus = rememberContextFocus("watch-history:${state.sourceKey}")
    var confirmSource by remember(state.sourceKey) { mutableStateOf<String?>(null) }
    val keys = remember(state.rows) { state.rows.flatMap { listOf("play:${it.channel.id}", "remove:${it.channel.id}") } }
    val controls = buildList {
        add("close")
        if (state.error != null && !state.isSaving) add("retry")
        if (!state.isLoading && !state.isSaving && state.totalRecords > 0) add("clear")
    }
    val ready = !state.isLoading && !state.isSaving && confirmSource == null
    focus.Resume(setOf("rows", "controls"), "rows")
    focus.Maintain("rows", keys, ready)
    focus.Restore("rows", keys, ready, onEmpty = { focus.request("controls", "close") },
        scrollTo = { index ->
            val row = index / 2
            if (list.layoutInfo.visibleItemsInfo.none { it.index == row }) list.scrollToItem(row)
        })
    focus.Restore("controls", controls, ready = confirmSource == null && !state.isSaving)
    Column(Modifier.fillMaxSize().then(focus.inputModifier()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.history_title), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall)
            AppButton(stringResource(R.string.history_close), onBack,
                focus.target("controls", "close", 0).testTag("history-close"))
        }
        Text(stringResource(R.string.history_hint), style = MaterialTheme.typography.bodySmall)
        if (state.error != null) {
            Text(stringResource(when (state.error) {
                WatchHistoryError.READ -> R.string.history_read_error
                WatchHistoryError.CHANNELS -> R.string.history_channels_error
                WatchHistoryError.WRITE -> R.string.history_write_error
            }), color = MaterialTheme.colorScheme.error)
            AppButton(stringResource(R.string.history_retry), onRetry,
                focus.target("controls", "retry", 1), enabled = !state.isSaving)
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
                        Surface(onClick = {
                            focus.record("rows", "play:${row.channel.id}", index * 2)
                            onPlay(state.sourceKey, row.channel.id)
                        }, enabled = !state.isSaving,
                            modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                .then(focus.target("rows", "play:${row.channel.id}", index * 2))
                                .testTag("history-play-${row.channel.id}")
                                .onFocusChanged { focused = it.isFocused },
                            color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.medium,
                            border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ChannelLogo(row.channel.name, row.channel.logoUrl, Modifier.size(42.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(row.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (row.channel.id == playingId) Text(stringResource(R.string.history_playing),
                                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                    else row.channel.group?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        AppButton(stringResource(R.string.history_remove), {
                            focus.request("rows", place = FocusBookmarks.Place("play:${row.channel.id}", index * 2))
                            onRemove(state.sourceKey, row.channel.id)
                        }, focus.target("rows", "remove:${row.channel.id}", index * 2 + 1)
                            .testTag("history-remove-${row.channel.id}"), enabled = !state.isSaving)
                    }
                }
            }
        }
        AppButton(stringResource(if (state.isSaving) R.string.history_saving else R.string.history_clear),
            {
                focus.record("controls", "clear", 2); confirmSource = state.sourceKey
            }, focus.target("controls", "clear", 2), enabled = !state.isLoading && !state.isSaving && state.totalRecords > 0)
    }
    if (confirmSource != null) {
        val confirmation = rememberContextFocus("history-clear-confirmation")
        fun dismiss() { confirmSource = null; focus.request("controls", "clear") }
        AlertDialog(onDismissRequest = ::dismiss, modifier = confirmation.inputModifier(),
            title = { Text(stringResource(R.string.history_clear)) },
            text = {
                val windowFocused = LocalWindowInfo.current.isWindowFocused
                confirmation.Resume(setOf("confirm"), "confirm", "cancel")
                confirmation.Restore("confirm", listOf("cancel", "clear"), ready = windowFocused)
                Text(stringResource(R.string.history_clear_question))
            },
            confirmButton = { AppButton(stringResource(R.string.history_clear), {
                val source = confirmSource; confirmSource = null
                if (source != null) { focus.request("rows", place = null); onClear(source) }
            }, confirmation.target("confirm", "clear", 1)) },
            dismissButton = { AppButton(stringResource(R.string.history_cancel), ::dismiss,
                confirmation.target("confirm", "cancel", 0)) })
    }
}