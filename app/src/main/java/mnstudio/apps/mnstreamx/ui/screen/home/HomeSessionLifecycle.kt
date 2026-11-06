package mnstudio.apps.mnstreamx.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import mnstudio.apps.mnstreamx.viewmodel.HomeViewModel

@Composable
internal fun HomeSessionLifecycle(viewModel: HomeViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setPreparationActive(true)
                Lifecycle.Event.ON_STOP -> { viewModel.setPreparationActive(false);viewModel.flushBrowseSession() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        viewModel.setPreparationActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.setPreparationActive(false)
            viewModel.flushBrowseSession()
        }
    }
}