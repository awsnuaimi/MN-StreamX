package mnstudio.apps.mnstreamx.ui.screen.vod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import mnstudio.apps.mnstreamx.viewmodel.VodViewModel

@Composable
internal fun VodScreenWorkLifetime(model: VodViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner,model) {
        val observer = LifecycleEventObserver { _,event ->
            if (event == Lifecycle.Event.ON_START) model.setPreparationActive(true)
            if (event == Lifecycle.Event.ON_STOP) model.setPreparationActive(false)
        }
        owner.lifecycle.addObserver(observer)
        model.setPreparationActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer);model.setPreparationActive(false) }
    }
}