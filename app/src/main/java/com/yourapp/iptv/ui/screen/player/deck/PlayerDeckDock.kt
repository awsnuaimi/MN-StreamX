package com.yourapp.iptv.ui.screen.player.deck

import androidx.compose.foundation.layout.*
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = if (appearance.showChannelButtons) 7 else 5
        val width = ((maxWidth - 24.dp) / count).coerceIn(72.dp, 114.dp)
        LazyRow(Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
        if (appearance.showChannelButtons) {
            item("previous") { PlayerDeckButton(DeckIcon.PREVIOUS, "السابق", onPrevious, modifier = Modifier.width(width), enabled = canChange) }
            item("next") { PlayerDeckButton(DeckIcon.NEXT, "التالي", onNext, modifier = Modifier.width(width), enabled = canChange) }
        }
        item("favorite") { PlayerDeckButton(DeckIcon.FAVORITE, when {
            favorites.isLoading -> "قراءة المفضلة…"
            favorites.isSaving -> "حفظ المفضلة…"
            else -> "المفضلة"
        }, onFavorite, Modifier.width(width).focusRequester(favoriteFocus), selected = isFavorite) }
        item("controls") { PlayerDeckButton(DeckIcon.CONTROLS, "التحكم", onControls, Modifier.width(width)) }
        item("details") { PlayerDeckButton(DeckIcon.DETAILS, "التفاصيل", onDetails, Modifier.width(width).focusRequester(detailsFocus)) }
        item("appearance") { PlayerDeckButton(DeckIcon.APPEARANCE, "المظهر", onAppearance, Modifier.width(width)) }
        item("back") { PlayerDeckButton(DeckIcon.BACK, "رجوع", onBack, Modifier.width(width)) }
        }
    }
}