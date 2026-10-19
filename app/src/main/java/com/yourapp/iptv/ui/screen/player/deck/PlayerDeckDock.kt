package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.ui.state.FavoriteUiState

@Composable
internal fun PlayerDeckDock(appearance: PlayerAppearance, canChange: Boolean, isFavorite: Boolean,
    favorites: FavoriteUiState, favoriteFocus: FocusRequester, detailsFocus: FocusRequester,
    onPrevious: () -> Unit, onNext: () -> Unit, onControls: () -> Unit, onDetails: () -> Unit,
    onFavorite: () -> Unit, onAppearance: () -> Unit, onBack: () -> Unit) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (appearance.showChannelButtons) {
            item("previous") { PlayerDeckButton(DeckIcon.PREVIOUS, "السابق", onPrevious, enabled = canChange) }
            item("next") { PlayerDeckButton(DeckIcon.NEXT, "التالي", onNext, enabled = canChange) }
        }
        item("favorite") { PlayerDeckButton(DeckIcon.FAVORITE, when {
            favorites.isLoading -> "قراءة المفضلة…"
            favorites.isSaving -> "حفظ المفضلة…"
            else -> "المفضلة"
        }, onFavorite, Modifier.focusRequester(favoriteFocus), selected = isFavorite) }
        item("controls") { PlayerDeckButton(DeckIcon.CONTROLS, "التحكم", onControls) }
        item("details") { PlayerDeckButton(DeckIcon.DETAILS, "التفاصيل", onDetails, Modifier.focusRequester(detailsFocus)) }
        item("appearance") { PlayerDeckButton(DeckIcon.APPEARANCE, "المظهر", onAppearance) }
        item("back") { PlayerDeckButton(DeckIcon.BACK, "رجوع", onBack) }
    }
}