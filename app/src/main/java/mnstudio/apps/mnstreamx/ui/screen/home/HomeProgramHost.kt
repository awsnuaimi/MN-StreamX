package mnstudio.apps.mnstreamx.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.ui.state.HomeProgramUiState
import mnstudio.apps.mnstreamx.viewmodel.HomeProgramViewModel

@Composable
internal fun HomeProgramHost(
    viewModel: HomeProgramViewModel = hiltViewModel(),
    content: @Composable (HomeProgramUiState, (Channel?) -> Unit) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.selectChannel(null) } }
    content(state, viewModel::selectChannel)
}