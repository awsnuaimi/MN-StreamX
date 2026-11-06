package mnstudio.apps.mnstreamx.ui.screen.home

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mnstudio.apps.mnstreamx.domain.parental.ParentalSection
import mnstudio.apps.mnstreamx.ui.screen.parental.LocalParentalSession
import mnstudio.apps.mnstreamx.ui.screen.player.PlayerCaptionDialog
import mnstudio.apps.mnstreamx.ui.state.CaptionUiState
import mnstudio.apps.mnstreamx.viewmodel.CaptionViewModel

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