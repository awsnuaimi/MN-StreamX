package com.yourapp.iptv.ui.screen.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourapp.iptv.diagnostics.DiagnosticSharing
import com.yourapp.iptv.domain.diagnostics.DiagnosticText
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.viewmodel.DiagnosticsViewModel
import com.yourapp.iptv.viewmodel.HomeAppearanceViewModel

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, model: DiagnosticsViewModel = hiltViewModel(),
    appearance: HomeAppearanceViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val events by model.events.collectAsStateWithLifecycle()
    val style by appearance.appearance.collectAsStateWithLifecycle()
    val language = style?.language ?: HomeLanguage.ARABIC
    val text = remember(language) { DiagnosticText(language) }
    val context = LocalContext.current
    val sharing = remember(context) { DiagnosticSharing(context.applicationContext) }
    val owner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    DisposableEffect(owner,model) {
        val observer = LifecycleEventObserver { _,event ->
            if (event == Lifecycle.Event.ON_START) model.setVisible(true)
            if (event == Lifecycle.Event.ON_STOP) model.setVisible(false)
        }
        model.setVisible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);model.setVisible(false) }
    }
    LaunchedEffect(state.shareFile,state.shareRequest) {
        val file = state.shareFile ?: return@LaunchedEffect
        model.consumeShare()
        try { launcher.launch(sharing.intent(file,text.title())) }
        catch (_: RuntimeException) { model.shareFailed() }
    }
    LocalizedResources(language) {
        DiagnosticsContent(state,events,text,onBack,model::runChecks,model::cancel,{ model.export(language) },model::clearEvents)
    }
}