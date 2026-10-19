package com.yourapp.iptv.ui.screen.player

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.yourapp.iptv.ui.theme.NeonBlue

internal enum class PlayerMoreAction { FAVORITE, DETAILS, GUIDE, CHANNELS, CONTROLS, APPEARANCE, PREVIOUS, NEXT }

@Composable
internal fun PlayerMoreDialog(channel: Channel, appearance: PlayerAppearance, favorites: FavoriteUiState,
    canChange: Boolean, initial: PlayerMoreAction, onRemoteKey: (KeyEvent, Boolean) -> Boolean,
    onDismiss: () -> Unit, onAction: (PlayerMoreAction) -> Unit, onRetryFavorites: () -> Unit) {
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
                    Text("المزيد · أدوات القناة الحالية", color = NeonBlue, style = MaterialTheme.typography.titleLarge)
                    Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(actions, key = { it.name }) { action ->
                            MoreActionRow(action, channel.isFavorite, favorites,
                                enabled = when (action) {
                                    PlayerMoreAction.NEXT, PlayerMoreAction.PREVIOUS -> canChange
                                    else -> true
                                }, modifier = if (action == initial) Modifier.focusRequester(focus) else Modifier,
                                onClick = { onAction(action) })
                        }
                        item("feedback") { FavoriteFeedback(favorites, onRetryFavorites) }
                    }
                    AppButton("رجوع للبطاقات", onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MoreActionRow(action: PlayerMoreAction, isFavorite: Boolean, favorites: FavoriteUiState,
    enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val label = when (action) {
        PlayerMoreAction.FAVORITE -> when {
            favorites.isLoading -> "جارٍ قراءة المفضلة…"
            favorites.isSaving -> "جارٍ حفظ المفضلة…"
            isFavorite -> "إزالة من المفضلة"
            else -> "إضافة للمفضلة"
        }
        PlayerMoreAction.DETAILS -> "تفاصيل البرنامج"
        PlayerMoreAction.GUIDE -> "دليل البرامج"
        PlayerMoreAction.CHANNELS -> "قائمة القنوات"
        PlayerMoreAction.CONTROLS -> "التحكم بالتشغيل"
        PlayerMoreAction.APPEARANCE -> "مظهر المشغّل"
        PlayerMoreAction.PREVIOUS -> "القناة السابقة"
        PlayerMoreAction.NEXT -> "القناة التالية"
    }
    val icon = when (action) {
        PlayerMoreAction.FAVORITE -> DeckIcon.FAVORITE
        PlayerMoreAction.DETAILS -> DeckIcon.DETAILS
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