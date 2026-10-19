package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.PlaybackTechnicalInfo
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.ui.component.channel.FavoriteFeedback
import com.yourapp.iptv.ui.state.FavoriteUiState
import com.yourapp.iptv.ui.state.PlayerEpgUiState

@Composable
internal fun PlayerDeckOverlay(channel: Channel, channels: List<Channel>, currentIndex: Int,
    appearance: PlayerAppearance, epg: PlayerEpgUiState, technical: PlaybackTechnicalInfo, canChange: Boolean,
    favorites: FavoriteUiState, favoriteFocus: FocusRequester, detailsFocus: FocusRequester,
    focusCardId: String?, focusEpoch: Int, onSelect: (String) -> Unit, onGuide: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onChannels: () -> Unit, onControls: () -> Unit,
    onDetails: () -> Unit, onFavorite: () -> Unit, onRetryFavorites: () -> Unit,
    onAppearance: () -> Unit, onBack: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dense = maxHeight < 480.dp
        val alpha = appearance.panelShade.alpha
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.88f)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = (alpha - 0.18f).coerceAtLeast(0.4f)),
                Color.Black.copy(alpha = alpha), Color.Black.copy(alpha = (alpha + 0.12f).coerceAtMost(1f)))))
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerDeckInfo(channel, currentIndex + 1, channels.size, epg, appearance, technical, dense)
            PlayerDeckRail(channels, currentIndex, channel.id, epg.schedule.current(epg.nowMillis)?.title,
                appearance, focusCardId, focusEpoch, dense, onGuide, onChannels, onSelect)
            PlayerDeckDock(appearance, canChange, channel.isFavorite, favorites, favoriteFocus, detailsFocus,
                onPrevious, onNext, onControls, onDetails, onFavorite, onAppearance, onBack)
            FavoriteFeedback(favorites, onRetryFavorites)
            if (appearance.showHints) Text("INFO للأيقونات · ↑ ↓ للتنقل بين الصفوف · OK للاختيار · MENU للقنوات",
                color = Color(0xFFBCCBD9), style = MaterialTheme.typography.bodySmall)
        }
    }
}