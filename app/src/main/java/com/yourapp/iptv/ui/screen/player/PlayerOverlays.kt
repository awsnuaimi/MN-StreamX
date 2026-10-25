package com.yourapp.iptv.ui.screen.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlaybackTechnicalInfo
import com.yourapp.iptv.ui.screen.player.deck.PlayerDeckOverlay
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.PlayerEpgUiState

@Composable
fun PlayerInfoOverlay(
    channel: Channel?, position: Int, channels: List<Channel>, appearance: PlayerAppearance,
    epg: PlayerEpgUiState, technical: PlaybackTechnicalInfo, cardsOpen: Boolean,
    focused: Channel?, preview: PlayerEpgUiState, focusEpoch: Int, focusMore: Boolean,
    onMore: () -> Unit, onSelect: (String) -> Unit, onFocusChannel: (String?) -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.textSize.scale)) {
        if (channel != null) PlayerDeckOverlay(channel, channels, position - 1, appearance, epg, technical,
            cardsOpen, focused, preview, focusEpoch, focusMore, onMore, onSelect, onFocusChannel)
    }
}

@Composable
fun PlayerErrorOverlay(
    error: String, canRetry: Boolean, hasChannels: Boolean, focus: FocusRequester,
    onRetry: () -> Unit, onChannels: () -> Unit, onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(32.dp), color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.player_error), style = MaterialTheme.typography.titleMedium)
                Regex("ERROR_CODE_[A-Z0-9_]+").find(error)?.value?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (canRetry) AppButton(stringResource(R.string.player_retry), onRetry, Modifier.focusRequester(focus))
                    if (hasChannels) AppButton(stringResource(R.string.player_pick_channel), onChannels,
                        if (!canRetry) Modifier.focusRequester(focus) else Modifier)
                    AppButton(stringResource(R.string.player_back_channels), onBack,
                        if (!canRetry && !hasChannels) Modifier.focusRequester(focus) else Modifier)
                }
            }
        }
    }
}