package com.yourapp.iptv.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yourapp.iptv.R
import com.yourapp.iptv.ui.state.PlayerEpgUiState

@Composable
internal fun playerEpgStatus(state: PlayerEpgUiState): String = stringResource(when {
    state.isLoading -> R.string.player_epg_loading
    state.errorMessage != null -> R.string.player_epg_error
    !state.isSupported -> R.string.player_epg_unsupported
    state.schedule.isStale -> R.string.player_epg_stale
    else -> R.string.player_epg_missing
})