package mnstudio.apps.mnstreamx.ui.screen.player

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mnstudio.apps.mnstreamx.R
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.WatchHistory
import mnstudio.apps.mnstreamx.ui.component.channel.ChannelLogo
import mnstudio.apps.mnstreamx.ui.state.WatchHistoryError
import mnstudio.apps.mnstreamx.viewmodel.WatchHistoryViewModel

/** In-player, right-anchored quick recall. No new database or polling while watching. */
@Composable
internal fun PlayerRecentChannelsRail(
    playingId: String?, expectedSource: String,
    onDismiss: () -> Unit, onPlay: (Channel, String) -> Unit,
    history: WatchHistoryViewModel = hiltViewModel(key = "player-recent-channels")
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, history) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) history.resume()
            if (event == Lifecycle.Event.ON_STOP) history.pause()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) history.resume()
        else history.pause()
        onDispose { lifecycle.removeObserver(observer); history.pause() }
    }
    val state by history.uiState.collectAsStateWithLifecycle()
    val validSource = expectedSource.isNotBlank() && state.sourceKey == expectedSource && !state.needsSource
    val entries = remember(state.rows, validSource) {
        if (validSource) RecentRailNavigation.entries(state.rows) else emptyList()
    }
    val ids = remember(entries) { entries.map { it.channel.id } }
    var selectedId by remember(state.sourceKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(ids, playingId) {
        if (selectedId !in ids) selectedId = RecentRailNavigation.initial(ids, playingId)
    }
    val requester = remember { FocusRequester() }
    val list = rememberLazyListState()
    val selectedIndex = ids.indexOf(selectedId)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    LaunchedEffect(selectedIndex, ids.size) {
        if (selectedIndex >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            list.scrollToItem(selectedIndex)
        }
    }
    fun playSelected() {
        val row = entries.firstOrNull { it.channel.id == selectedId } ?: return
        if (validSource) onPlay(row.channel, state.sourceKey)
    }
    val keyInput = Modifier.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val pressed = native.action == KeyEvent.ACTION_DOWN && !native.isCanceled
        val released = native.action == KeyEvent.ACTION_UP && !native.isCanceled
        when (native.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (pressed) selectedId = RecentRailNavigation.move(ids, selectedId, playingId,
                    if (native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // Activate on release: it cannot reach the underlying player after closing the rail.
                if (released) {
                    if (state.error != null) history.reload() else playSelected()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (released) onDismiss(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> false
        }
    }
    val colors = MaterialTheme.colorScheme
    val error = state.error
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
        // CenterStart is physically right in RTL; the rail stays on the TV's right edge.
        contentAlignment = if (isRtl) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        // An increasing number of watched channels adds only the height required by the rows.
        val height = minOf(maxHeight, 76.dp + 62.dp * entries.size.coerceAtLeast(1))
        val width = minOf(maxWidth, 356.dp)
        Surface(
            modifier = Modifier.width(width).height(height)
                .focusRequester(requester).then(keyInput).focusable().testTag("player-recent-rail"),
            color = Color.Black.copy(alpha = 0.58f),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, colors.primary)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.player_recent_title), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entries.size}/${WatchHistory.LIMIT}", color = colors.primary,
                        style = MaterialTheme.typography.labelMedium)
                }
                when {
                    state.isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.primary)
                    }
                    error != null -> Text(stringResource(when (error) {
                        WatchHistoryError.READ -> R.string.history_read_error
                        WatchHistoryError.CHANNELS -> R.string.history_channels_error
                        WatchHistoryError.WRITE -> R.string.history_write_error
                    }), Modifier.weight(1f), color = Color.White)
                    entries.isEmpty() -> Text(stringResource(R.string.history_empty), Modifier.weight(1f),
                        color = Color.White)
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("player-recent-list"),
                        state = list, verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(entries, key = { _, row -> row.channel.id }) { _, row ->
                            val highlighted = row.channel.id == selectedId
                            val playing = row.channel.id == playingId
                            Surface(onClick = { onPlay(row.channel, state.sourceKey) },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                                    .semantics { selected = highlighted }
                                    .testTag("player-recent-${row.channel.id}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (highlighted) colors.primary.copy(alpha = 0.42f)
                                    else Color.White.copy(alpha = 0.07f),
                                border = if (highlighted) BorderStroke(2.dp, colors.primary) else null,
                                contentColor = Color.White
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    ChannelLogo(row.channel.name, row.channel.logoUrl, Modifier.size(36.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(row.channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                        val subtitle = if (playing) stringResource(R.string.history_playing)
                                            else row.channel.group.orEmpty()
                                        if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.76f))
                                    }
                                }
                            }
                        }
                    }
                }
                // The rail also accepts touch, but DPAD selection remains deterministic.
            }
        }
    }
}