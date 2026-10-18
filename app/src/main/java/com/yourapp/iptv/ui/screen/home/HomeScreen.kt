package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.tv.TvChannelGrid
import com.yourapp.iptv.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    onPlayChannel: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MN StreamX", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text("القنوات المباشرة · ${state.visibleChannels.size} / ${state.channels.size}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            AppButton("تحديث", viewModel::refresh, enabled = !state.isLoading)
            AppButton("الإعدادات", onSettings)
        }
        if (state.channels.isNotEmpty()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("البحث عن قناة") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.groups.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(selected = state.selectedGroup == null,
                            onClick = { viewModel.selectGroup(null) }, label = { Text("الكل") })
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
            state.visibleChannels.isEmpty() -> HomeMessage("لا توجد نتائج مطابقة.", "عرض الكل", viewModel::clearFilters)
            else -> TvChannelGrid(
                channels = state.visibleChannels,
                selectedChannelId = state.selectedChannelId,
                onChannelClick = { channel ->
                    viewModel.selectChannel(channel.id)
                    onPlayChannel(channel.id)
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