package mnstudio.apps.mnstreamx.ui.screen.home

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.ui.state.HomeGuideState
import mnstudio.apps.mnstreamx.viewmodel.HomeGuideViewModel

@Composable
internal fun HomeGuideHost(model: HomeGuideViewModel = hiltViewModel(),
    content: @Composable (HomeGuideState, (List<Channel>) -> Unit) -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    DisposableEffect(model) { onDispose { model.bind(emptyList()) } }
    content(state, model::bind)
}