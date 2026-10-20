package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.state.PlayerEpgUiState

@Composable
internal fun PlayerDeckOverlay(channel: Channel, channels: List<Channel>, currentIndex: Int,
    appearance: PlayerAppearance, epg: PlayerEpgUiState, technical: PlaybackTechnicalInfo,
    cardsOpen: Boolean, focused: Channel?, preview: PlayerEpgUiState, focusEpoch: Int,
    focusMore: Boolean, onMore: () -> Unit, onSelect: (String) -> Unit, onFocusChannel: (String?) -> Unit) {
    val inspected = if (cardsOpen) focused ?: channel else channel
    val isPreview = inspected.id != channel.id
    val shownEpg = if (!isPreview) epg else if (preview.channelId == inspected.id) preview
        else PlayerEpgUiState(channelId = inspected.id, isLoading = true)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dense = maxHeight < 650.dp
        val alpha = (appearance.panelShade.alpha + 0.1f).coerceAtMost(0.98f)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.75f)
            .background(Brush.verticalGradient(0f to Color.Transparent,
                0.06f to Color(0xFF080E19).copy(alpha = alpha),
                0.55f to Color(0xFF080E19).copy(alpha = alpha),
                1f to Color(0xFF080E19).copy(alpha = (alpha + 0.08f).coerceAtMost(1f))))
            .verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerDeckInfo(inspected, if (isPreview) channels.indexOfFirst { it.id == inspected.id } + 1 else currentIndex + 1,
                channels.size, shownEpg, appearance, if (isPreview) PlaybackTechnicalInfo() else technical, dense, isPreview)
            if (isPreview) Text(stringResource(R.string.player_still_watching, channel.name), color = Color(0xFFBCCBD9),
                maxLines = 1, style = MaterialTheme.typography.bodySmall)
            if (cardsOpen) PlayerDeckRail(channels, currentIndex, channel.id,
                appearance, focusEpoch, focusMore, onMore, onSelect, onFocusChannel)
            if (appearance.showHints) Text(
                stringResource(if (cardsOpen) R.string.player_cards_hint else R.string.player_watch_hint),
                color = Color(0xFFBCCBD9), style = MaterialTheme.typography.bodySmall)
        }
    }
}