package com.yourapp.iptv.ui.component.channel

import com.yourapp.iptv.ui.theme.AppThemeColors
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.FavoriteUiState

@Composable
fun FavoriteBadge(isFavorite: Boolean) {
    val description = stringResource(R.string.ui_favorite_badge)
    if (isFavorite) Text("★", color = AppThemeColors.favorite(MaterialTheme.colorScheme),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { contentDescription = description })
}

@Composable
fun FavoriteButton(isFavorite: Boolean, state: FavoriteUiState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    AppButton(when {
        state.isLoading -> stringResource(R.string.player_favorites_loading)
        state.isSaving -> stringResource(R.string.player_favorites_saving)
        isFavorite -> "★ " + stringResource(R.string.player_favorite_remove)
        else -> "☆ " + stringResource(R.string.player_favorite_add)
    }, onToggle, modifier)
}

@Composable
fun FavoriteFeedback(state: FavoriteUiState, onRetry: () -> Unit,
    messageOverride: String? = null, retryLabel: String? = null) {
    state.errorMessage?.let {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(messageOverride ?: stringResource(R.string.player_favorites_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            AppButton(retryLabel ?: stringResource(R.string.player_favorites_retry), onRetry, enabled = !state.isSaving)
        }
    }
}