package com.yourapp.iptv.ui.screen.home

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.ui.state.HomeGuideState
import com.yourapp.iptv.viewmodel.HomeGuideViewModel

@Composable
internal fun HomeGuideHost(model: HomeGuideViewModel = hiltViewModel(),
    content: @Composable (HomeGuideState, (List<Channel>) -> Unit) -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    DisposableEffect(model) { onDispose { model.bind(emptyList()) } }
    content(state, model::bind)
}