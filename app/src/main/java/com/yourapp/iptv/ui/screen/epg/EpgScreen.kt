package com.yourapp.iptv.ui.screen.epg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.ui.state.PlayerEpgUiState
import com.yourapp.iptv.viewmodel.EpgViewModel
import com.yourapp.iptv.viewmodel.PlayerEpgViewModel

@Composable
fun EpgScreen(
    onBack: () -> Unit, onPlayChannel: (String, String?, String, Boolean) -> Unit,
    returnedChannelId: String? = null, onReturnHandled: () -> Unit = {},
    viewModel: EpgViewModel = hiltViewModel(), epgViewModel: PlayerEpgViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteState.collectAsStateWithLifecycle()
    val loadedEpg by epgViewModel.uiState.collectAsStateWithLifecycle()
    val channel = state.channel
    val epg = if (loadedEpg.channelId == channel?.id) loadedEpg
        else PlayerEpgUiState(channelId = channel?.id, isLoading = channel != null)
    LaunchedEffect(channel) { epgViewModel.selectChannel(channel) }
    EpgGuideContent(state, favorites, epg, returnedChannelId, EpgGuideActions(
        back = onBack, query = viewModel::updateQuery, group = viewModel::selectGroup,
        favorites = viewModel::toggleFavoritesFilter, selectChannel = viewModel::selectChannel,
        selectProgram = viewModel::selectProgram, toggleFavorite = viewModel::toggleFavorite,
        retryFavorites = viewModel::retryFavorites, reload = viewModel::reload,
        clearFilters = viewModel::clearFilters, restoreChannel = viewModel::restoreChannel,
        returnHandled = onReturnHandled, play = { id ->
            val latest = viewModel.uiState.value
            if (latest.queue.indexOf(id) >= 0) {
                viewModel.selectChannel(id)
                onPlayChannel(id, latest.selectedGroup, latest.query, latest.favoritesOnly)
            }
        }
    ))
}