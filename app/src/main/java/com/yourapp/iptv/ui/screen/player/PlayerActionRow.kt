package com.yourapp.iptv.ui.screen.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.model.PlayerInfoStyle
import com.yourapp.iptv.ui.component.channel.FavoriteButton
import com.yourapp.iptv.ui.component.channel.FavoriteFeedback
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.FavoriteUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerActionRow(appearance: PlayerAppearance, hasChannel: Boolean, isFavorite: Boolean,
    canChange: Boolean, total: Int, favorites: FavoriteUiState, favoriteFocus: FocusRequester,
    detailsFocus: FocusRequester, onPrevious: () -> Unit, onNext: () -> Unit, onChannels: () -> Unit,
    onControls: () -> Unit, onDetails: () -> Unit, onToggleFavorite: () -> Unit, onRetryFavorites: () -> Unit) {
    val compact = appearance.style == PlayerInfoStyle.COMPACT
    @Composable fun Action(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
        if (compact) PlayerActionButton(text, onClick, modifier, enabled)
        else AppButton(text, onClick, modifier, enabled)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (appearance.showChannelButtons) Action("السابق", canChange, onPrevious)
            Action("قائمة القنوات", total > 0, onChannels)
            if (appearance.showChannelButtons) Action("التالي", canChange, onNext)
            Action("التحكم", hasChannel, onControls)
            Action("تفاصيل البرنامج", hasChannel, onDetails, Modifier.focusRequester(detailsFocus))
            if (hasChannel) {
                if (compact) Action(when {
                    favorites.isLoading -> "قراءة المفضلة…"
                    favorites.isSaving -> "حفظ المفضلة…"
                    isFavorite -> "★ المفضلة"
                    else -> "☆ المفضلة"
                }, true, onToggleFavorite, Modifier.focusRequester(favoriteFocus))
                else FavoriteButton(isFavorite, favorites, onToggleFavorite, Modifier.focusRequester(favoriteFocus))
            }
        }
        FavoriteFeedback(favorites, onRetryFavorites)
        if (appearance.showHints) Text("INFO المفضلة · MENU القائمة · CH+ / CH− تبديل · OK تحكم",
            color = Color(0xFFCCD9E8), style = MaterialTheme.typography.bodySmall)
    }
}