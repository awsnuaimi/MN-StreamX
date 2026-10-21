package com.yourapp.iptv.ui.screen.player

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.ui.component.channel.FavoriteFeedback
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.screen.player.deck.*
import com.yourapp.iptv.ui.state.FavoriteUiState
import com.yourapp.iptv.ui.state.LastChannelError
import com.yourapp.iptv.ui.state.LastChannelUiState
import com.yourapp.iptv.ui.theme.NeonBlue

internal enum class PlayerMoreAction { FAVORITE, LAST_CHANNEL, HISTORY, DETAILS, GUIDE, CHANNELS, CONTROLS, APPEARANCE, PREVIOUS, NEXT, CAPTIONS }

@Composable
internal fun PlayerMoreDialog(channel: Channel, appearance: PlayerAppearance, favorites: FavoriteUiState,
    canChange: Boolean, initial: PlayerMoreAction, onRemoteKey: (KeyEvent, Boolean) -> Boolean,
    onDismiss: () -> Unit, onAction: (PlayerMoreAction) -> Unit, onRetryFavorites: () -> Unit,
    lastChannel: LastChannelUiState = LastChannelUiState(), onRetryLastChannel: () -> Unit = {}) {
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val actions = remember(appearance.showChannelButtons) {
        PlayerMoreAction.entries.filter { appearance.showChannelButtons || it !in setOf(PlayerMoreAction.PREVIOUS, PlayerMoreAction.NEXT) }
    }
    LaunchedEffect(initial) {
        list.scrollToItem(actions.indexOf(initial).coerceAtLeast(0)); withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textSize.scale)) {
            Surface(Modifier.fillMaxWidth().heightIn(max = 480.dp)
                .onPreviewKeyEvent { onRemoteKey(it.nativeKeyEvent, false) },
                color = Color(0xFF101C32), contentColor = Color.White, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.player_more_title), color = NeonBlue, style = MaterialTheme.typography.titleLarge)
                    Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    LastChannelFeedback(lastChannel, onRetryLastChannel)
                    LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(actions, key = { it.name }) { action ->
                            MoreActionRow(action, channel.isFavorite, favorites, lastChannel,
                                enabled = when (action) {
                                    PlayerMoreAction.LAST_CHANNEL -> lastChannel.canRecall
                                    PlayerMoreAction.NEXT, PlayerMoreAction.PREVIOUS -> canChange
                                    else -> true
                                }, modifier = if (action == initial) Modifier.focusRequester(focus) else Modifier,
                                onClick = { if (action != PlayerMoreAction.LAST_CHANNEL || !lastChannel.isSwitching) onAction(action) })
                        }
                        item("feedback") { FavoriteFeedback(favorites, onRetryFavorites,
                            messageOverride = stringResource(R.string.player_favorites_error),
                            retryLabel = stringResource(R.string.player_favorites_retry)) }
                    }
                    AppButton(stringResource(R.string.player_back_cards), onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MoreActionRow(action: PlayerMoreAction, isFavorite: Boolean, favorites: FavoriteUiState,
    lastChannel: LastChannelUiState,
    enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(when (action) {
        PlayerMoreAction.FAVORITE -> when {
            favorites.isLoading -> R.string.player_favorites_loading
            favorites.isSaving -> R.string.player_favorites_saving
            isFavorite -> R.string.player_favorite_remove
            else -> R.string.player_favorite_add
        }
        PlayerMoreAction.DETAILS -> R.string.player_details
        PlayerMoreAction.LAST_CHANNEL -> when {
            lastChannel.isSwitching -> R.string.last_channel_switching
            lastChannel.isLoading -> R.string.last_channel_loading
            else -> R.string.last_channel_title
        }
        PlayerMoreAction.CAPTIONS -> R.string.caption_title
        PlayerMoreAction.HISTORY -> R.string.history_title
        PlayerMoreAction.GUIDE -> R.string.player_guide
        PlayerMoreAction.CHANNELS -> R.string.player_channels
        PlayerMoreAction.CONTROLS -> R.string.player_controls
        PlayerMoreAction.APPEARANCE -> R.string.player_appearance
        PlayerMoreAction.PREVIOUS -> R.string.player_previous_channel
        PlayerMoreAction.NEXT -> R.string.player_next_channel
    })
    val icon = when (action) {
        PlayerMoreAction.FAVORITE -> DeckIcon.FAVORITE
        PlayerMoreAction.DETAILS -> DeckIcon.DETAILS
        PlayerMoreAction.LAST_CHANNEL -> DeckIcon.LAST_CHANNEL
        PlayerMoreAction.CAPTIONS -> DeckIcon.DETAILS
        PlayerMoreAction.HISTORY -> DeckIcon.HISTORY
        PlayerMoreAction.GUIDE -> DeckIcon.GUIDE
        PlayerMoreAction.CHANNELS -> DeckIcon.CHANNELS
        PlayerMoreAction.CONTROLS -> DeckIcon.CONTROLS
        PlayerMoreAction.APPEARANCE -> DeckIcon.APPEARANCE
        PlayerMoreAction.PREVIOUS -> DeckIcon.PREVIOUS
        PlayerMoreAction.NEXT -> DeckIcon.NEXT
    }
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.medium, color = if (focused) Color(0xFF164362) else Color(0xFF17243D),
        border = if (focused) BorderStroke(2.dp, NeonBlue) else null) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerDeckIcon(icon, if (!enabled) Color.Gray else if (isFavorite && action == PlayerMoreAction.FAVORITE)
                Color(0xFFFFD54F) else NeonBlue, filled = isFavorite && action == PlayerMoreAction.FAVORITE)
            Text(label, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun LastChannelFeedback(state: LastChannelUiState, retry: () -> Unit) {
    val message = when (state.error) {
        LastChannelError.HISTORY -> R.string.history_read_error
        LastChannelError.LOOKUP -> R.string.last_channel_lookup_error
        LastChannelError.UNAVAILABLE -> R.string.last_channel_unavailable
        LastChannelError.SOURCE_CHANGED -> R.string.history_source_changed
        null -> when {
            state.isLoading -> R.string.last_channel_loading
            !state.canRecall -> R.string.last_channel_empty
            else -> R.string.last_channel_hint
        }
    }
    Text(stringResource(message), style = MaterialTheme.typography.bodySmall,
        color = if (state.error == null) Color(0xFFB1C3D8) else MaterialTheme.colorScheme.error)
    if (state.error != null && state.error != LastChannelError.SOURCE_CHANGED)
        AppButton(stringResource(R.string.history_retry), retry)
}