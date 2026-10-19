package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.channel.FavoriteFeedback
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.tv.TvChannelGrid
import com.yourapp.iptv.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onPlayChannel: (String, String?, String, Boolean) -> Unit,
    onOpenGuide: (String?, String?, String, Boolean) -> Unit,
    returnedChannelId: String? = null,
    onReturnHandled: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteState.collectAsStateWithLifecycle()
    LaunchedEffect(returnedChannelId) {
        returnedChannelId?.let { viewModel.selectChannel(it); onReturnHandled() }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeHeader(state.visibleChannels.size, state.channels.size, state.isLoading,
            onRefresh = viewModel::refresh, onSettings = onSettings,
            onGuide = { onOpenGuide(state.selectedChannelId, state.selectedGroup, state.query, state.favoritesOnly) })
        if (state.channels.isNotEmpty()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("البحث عن قناة") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
            run {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = state.selectedGroup == null && !state.favoritesOnly,
                            onClick = { viewModel.selectGroup(null) }, label = { Text("الكل") })
                    }
                    item {
                        FilterChip(selected = state.favoritesOnly,
                            onClick = viewModel::toggleFavoritesFilter, label = { Text("★ المفضلة") })
                    }
                    items(state.groups, key = { it }) { group ->
                        FilterChip(selected = state.selectedGroup == group,
                            onClick = { viewModel.selectGroup(group) }, label = { Text(group) })
                    }
                }
            }
        }
        if (state.isLoading && state.channels.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        FavoriteFeedback(favorites, viewModel::retryFavorites)
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when {
            state.needsSource -> HomeMessage("اختر مصدر القنوات وأدخل رابط M3U أو بيانات Xtream.", "فتح الإعدادات", onSettings)
            state.isLoading && state.channels.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("جارٍ تحميل قائمة القنوات...")
                    }
                }
            }
            state.channels.isEmpty() -> HomeMessage("لا توجد قنوات محمّلة.", "إعادة المحاولة", viewModel::refresh)
            state.visibleChannels.isEmpty() -> HomeMessage(
                if (state.favoritesOnly) "لا توجد قنوات مفضلة تطابق البحث والتصنيف. أضف قناة من المشغّل أو دليل البرامج."
                else "لا توجد نتائج مطابقة.", "عرض الكل", viewModel::clearFilters)
            else -> TvChannelGrid(
                channels = state.visibleChannels,
                selectedChannelId = state.selectedChannelId,
                onChannelClick = { channel ->
                    viewModel.selectChannel(channel.id)
                    onPlayChannel(channel.id, state.selectedGroup, state.query, state.favoritesOnly)
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HomeMessage(text: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            AppButton(action, onAction)
        }
    }
}