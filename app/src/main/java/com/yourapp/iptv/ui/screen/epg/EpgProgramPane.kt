package com.yourapp.iptv.ui.screen.epg

import com.yourapp.iptv.ui.theme.AppThemeColors
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.model.EpgProgramSelection
import com.yourapp.iptv.ui.component.channel.FavoriteButton
import com.yourapp.iptv.ui.state.FavoriteUiState
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun EpgProgramPane(
    channel: Channel, epg: PlayerEpgUiState, selectedKey: String?, request: EpgFocusRequest,
    onSelectProgram: (String) -> Unit, onPlay: () -> Unit, onFocused: () -> Unit,
    favorites: FavoriteUiState, onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val programs = remember(epg.schedule.programs) {
        epg.schedule.programs.sortedBy { it.startTime }.distinctBy(EpgProgramSelection::key)
    }
    val selected = EpgProgramSelection.resolve(programs, selectedKey, epg.nowMillis)
    val resolvedKey = selected?.let(EpgProgramSelection::key)
    val missingSelection = selectedKey != null && programs.none { EpgProgramSelection.key(it) == selectedKey }
    val focus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val list = key(channel.id) { rememberLazyListState() }
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(zone, locale) { DateTimeFormatter.ofPattern("dd/MM HH:mm", locale).withZone(zone) }
    LaunchedEffect(request.serial, missingSelection) {
        if (request.area == EpgFocusArea.PROGRAMS) {
            val index = programs.indexOfFirst { EpgProgramSelection.key(it) == resolvedKey }
            if (index >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
            withFrameNanos { }
            runCatching { if (index >= 0) focus.requestFocus() else playFocus.requestFocus() }
        }
    }
    Surface(modifier, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxSize().padding(12.dp)
            .onFocusChanged { if (it.hasFocus) onFocused() }.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.ui_epg_provider_time), style = MaterialTheme.typography.bodySmall)
                }
                AppButton(stringResource(R.string.ui_watch_live), onPlay, Modifier.focusRequester(playFocus))
            }
            FavoriteButton(channel.isFavorite, favorites, onToggleFavorite)
            if (epg.schedule.isStale) {
                Text(stringResource(R.string.ui_epg_cached), color = AppThemeColors.warning(MaterialTheme.colorScheme),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (programs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (epg.isLoading) CircularProgressIndicator(Modifier.size(28.dp))
                        Text(when {
                            epg.isLoading -> stringResource(R.string.ui_epg_loading)
                            epg.errorMessage != null -> stringResource(R.string.player_epg_error)
                            !epg.isSupported -> stringResource(R.string.ui_epg_unsupported)
                            else -> stringResource(R.string.ui_epg_missing)
                        }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(programs, key = EpgProgramSelection::key) { program ->
                        val key = EpgProgramSelection.key(program)
                        EpgProgramCard(program, key == resolvedKey, epg.nowMillis, formatter,
                            onSelect = { onSelectProgram(key) }, onPlay = onPlay,
                            modifier = if (key == resolvedKey) Modifier.focusRequester(focus) else Modifier)
                    }
                }
            }
            Text(stringResource(R.string.ui_epg_ok_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}