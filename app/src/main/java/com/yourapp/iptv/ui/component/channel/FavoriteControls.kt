package com.yourapp.iptv.ui.component.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.FavoriteUiState

@Composable
fun FavoriteBadge(isFavorite: Boolean) {
    if (isFavorite) Text("★", color = Color(0xFFFFD54F),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { contentDescription = "قناة ضمن المفضلة" })
}

@Composable
fun FavoriteButton(isFavorite: Boolean, state: FavoriteUiState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    AppButton(when {
        state.isLoading -> "جارٍ قراءة المفضلة…"
        state.isSaving -> "جارٍ حفظ المفضلة…"
        isFavorite -> "★ إزالة من المفضلة"
        else -> "☆ إضافة للمفضلة"
    }, onToggle, modifier)
}

@Composable
fun FavoriteFeedback(state: FavoriteUiState, onRetry: () -> Unit) {
    state.errorMessage?.let { message ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            AppButton("تحديث المفضلة", onRetry, enabled = !state.isSaving)
        }
    }
}