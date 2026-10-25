package com.yourapp.iptv.ui.screen.history

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.viewmodel.WatchHistoryViewModel

@Composable
fun WatchHistoryScreen(onBack: () -> Unit, onPlay: (String, String) -> Unit,
    viewModel: WatchHistoryViewModel = hiltViewModel()) {
    BackHandler(onBack = onBack)
    WatchHistoryLifecycle(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WatchHistoryContent(state, null, { source, id ->
        viewModel.playbackChannel(source, id)?.let { onPlay(it.id, source) }
    }, viewModel::remove, viewModel::clear, viewModel::reload, onBack)
}

@Composable
internal fun WatchHistoryDialog(playingId: String?, onBack: () -> Unit, onPlay: (Channel, String) -> Unit,
    viewModel: WatchHistoryViewModel = hiltViewModel(key = "player-watch-history")) {
    WatchHistoryLifecycle(viewModel)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.90f).fillMaxHeight(0.88f), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            WatchHistoryContent(state, playingId, { source, id ->
                viewModel.playbackChannel(source, id)?.let { onPlay(it, source) }
            }, viewModel::remove, viewModel::clear, viewModel::reload, onBack)
        }
    }
}

@Composable
private fun WatchHistoryLifecycle(viewModel: WatchHistoryViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.resume()
            if (event == Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.resume() else viewModel.pause()
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.pause() }
    }
}