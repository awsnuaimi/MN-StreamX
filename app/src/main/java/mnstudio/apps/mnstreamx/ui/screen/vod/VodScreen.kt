package mnstudio.apps.mnstreamx.ui.screen.vod

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.viewmodel.HomeAppearanceViewModel
import mnstudio.apps.mnstreamx.viewmodel.VodViewModel
import mnstudio.apps.mnstreamx.viewmodel.VodProgressViewModel

@Composable
fun VodScreen(kind: VodKind, onBack: () -> Unit, onPlay: (VodKind, String, String, String, String?, String?) -> Unit,
    model: VodViewModel = hiltViewModel(), appearanceModel: HomeAppearanceViewModel = hiltViewModel(),
    progressModel: VodProgressViewModel = hiltViewModel()) {
    VodScreenWorkLifetime(model)
    val state by model.uiState.collectAsStateWithLifecycle()
    val history by progressModel.scopedHistory.collectAsStateWithLifecycle()
    val appearance by appearanceModel.appearance.collectAsStateWithLifecycle()
    val language = appearance?.language ?: HomeLanguage.ARABIC
    val labels = remember(language) { VodLabels(language) }
    val source = state.sourceKey
    LaunchedEffect(kind) { model.load(kind) }
    val progress = if (history.sourceKey == source) history.entries else emptyList()
    val completed = if (history.sourceKey == source) history.completed else emptyList()
    VodContent(state, progress, labels, VodActions(onBack, { model.load(kind, refresh = true) },
        model::search, { model.filter(it) }, { model.filter(state.categoryId, retry = true) },
        model::open, model::close, { state.selected?.let(model::open) }) { chosenKind, id, ext, title, series ->
        if (source != null && model.uiState.value.sourceKey == source) {
            model.close()
            onPlay(chosenKind, id, ext, title, source, series)
        }
    }, completed)
}