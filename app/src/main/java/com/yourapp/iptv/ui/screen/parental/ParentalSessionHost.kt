package com.yourapp.iptv.ui.screen.parental

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.parental.ParentalState
import com.yourapp.iptv.domain.parental.ParentalRenderSession
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.ParentalViewModel
import kotlinx.coroutines.delay

data class ParentalUiSession(val state: ParentalState = ParentalState(ready = true), val language: HomeLanguage = HomeLanguage.ARABIC,
    val model: ParentalViewModel? = null)
val LocalParentalSession = staticCompositionLocalOf { ParentalUiSession() }

@Composable
fun ParentalSessionHost(model: ParentalViewModel, language: HomeLanguage,
    content: @Composable (String?, () -> Unit) -> Unit) {
    // Collect while paused too, so locking disposes the previous playback and its scoped ViewModels.
    val access by model.access.collectAsState()
    val request by model.pinRequest.collectAsState()
    val route by model.pendingRoute.collectAsState()
    val editor by model.editor.collectAsState()
    val external by model.externalFlow.collectAsState()
    val revision by model.renderReset.collectAsState()
    val labels = remember(language) { ParentalLabels(language) }
    val stores: ParentalNavigationStores = viewModel()
    val sessions = remember { ParentalRenderSession() }
    val frame = sessions.frame(access,external != null,revision)
    SideEffect { external?.takeIf { !it.awaitingResult && access.unlocked }?.let { model.consumeExternal(it.token) } }
    if (!access.ready || access.storageError) {
        stores.clear()
        Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) {
            if (!access.ready) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(labels.text("تعذر قراءة الحماية. يبقى المحتوى مقفولًا.","Protection could not be read. Content stays locked.",
                    "Schutz konnte nicht gelesen werden. Inhalte bleiben gesperrt."))
                AppButton(labels.text("إعادة المحاولة","Retry","Erneut versuchen"),model::retryStorage)
            }
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (frame.hidden) 0f else 1f }
            .then(if (frame.hidden) Modifier.clearAndSetSemantics { } else Modifier)
            .onPreviewKeyEvent { frame.hidden }.pointerInput(frame.hidden) {
                if (frame.hidden) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }) {
            CompositionLocalProvider(LocalParentalSession provides ParentalUiSession(frame.state,language,model)) {
                val navigationOwner = stores.owner(frame.owner)
                key(frame.owner) { SessionViewModels(navigationOwner) { content(route,model::routeHandled) } }
            }
        }
        if (frame.hidden) RetainedFlowLock(labels,external?.awaitingResult == true,model)
        if (request != null) key(access.epoch,request) {
            var remaining by remember { mutableLongStateOf(model.control.remainingSeconds()) }
            LaunchedEffect(editor.result) { while (true) { remaining = model.control.remainingSeconds(); delay(500) } }
            PinEntryDialog(labels.pin(),labels,editor.busy,
                labels.message(editor.result,remaining),model::dismissPin,model::unlock,canSubmit = remaining == 0L)
        }
    }
}

@Composable
private fun RetainedFlowLock(labels: ParentalLabels, waiting: Boolean, model: ParentalViewModel) {
    val focus = remember { FocusRequester() }
    BackHandler { model.cancelExternal() }
    Surface(Modifier.fillMaxSize(),color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp,Alignment.CenterVertically)) {
            Text(labels.text("تم قفل التطبيق. الإجراء السابق محفوظ مؤقتًا.",
                "App locked. The previous action is temporarily retained.","App gesperrt. Der vorherige Vorgang wird vorübergehend aufbewahrt."))
            if (waiting) Text(labels.text("أكمل الإجراء الخارجي أو ألغِه.","Finish or cancel the external action.",
                "Externen Vorgang abschließen oder abbrechen."))
            if (!waiting) AppButton(labels.unlock(),{ model.requestPin() },Modifier.focusRequester(focus))
            AppButton(labels.text("إلغاء الإجراء وفتح المحتوى المسموح","Cancel action and open public content",
                "Vorgang abbrechen und öffentliche Inhalte öffnen"),model::cancelExternal,
                if (waiting) Modifier.focusRequester(focus) else Modifier)
        }
    }
    LaunchedEffect(waiting) { focus.requestFocus() }
}

@Composable
internal fun SessionViewModels(owner: ViewModelStoreOwner, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner,content = content)
}