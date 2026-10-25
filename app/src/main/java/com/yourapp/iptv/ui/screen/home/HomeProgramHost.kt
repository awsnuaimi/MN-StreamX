package com.yourapp.iptv.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.state.HomeProgramUiState
import com.yourapp.iptv.viewmodel.HomeProgramViewModel

/** Only composed on the dashboard; leaving it cancels its guide subscription. */
@Composable
internal fun HomeProgramHost(
    viewModel: HomeProgramViewModel = hiltViewModel(),
    content: @Composable (HomeProgramUiState, (Channel?) -> Unit) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.selectChannel(null) } }
    content(state, viewModel::selectChannel)
}