package com.yourapp.iptv.ui.screen.home

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.domain.parental.ParentalSection
import com.yourapp.iptv.ui.screen.parental.LocalParentalSession
import com.yourapp.iptv.ui.screen.player.PlayerCaptionDialog
import com.yourapp.iptv.ui.state.CaptionUiState
import com.yourapp.iptv.viewmodel.CaptionViewModel

/** Reads existing preferences. No audio is bound, so no speech model or translation session starts. */
@Composable
internal fun HomeCaptionHost(model: CaptionViewModel = hiltViewModel(),
    content: @Composable (CaptionUiState, () -> Unit) -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val access = LocalParentalSession.current
    var open by remember { mutableStateOf(false) }
    val allowed = access.state.allows(ParentalSection.SETTINGS)
    LaunchedEffect(allowed, access.state.epoch) { if (!allowed) open = false }
    DisposableEffect(model) { onDispose { model.bind(null, null) } }
    content(state) {
        if (allowed) open = true else access.model?.requestPin()
    }
    if (open && allowed) PlayerCaptionDialog(model, null) { open = false }
}