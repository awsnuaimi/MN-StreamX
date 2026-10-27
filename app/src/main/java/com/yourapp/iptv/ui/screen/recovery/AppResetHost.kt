package com.yourapp.iptv.ui.screen.recovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.HomeLanguage
import com.yourapp.iptv.domain.recovery.*
import com.yourapp.iptv.domain.security.SecureStorageFailure
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.viewmodel.AppResetViewModel

private val LocalAppReset = staticCompositionLocalOf<AppResetViewModel?> { null }

/** The normal app is not composed until the durable reset marker has been checked. */
@Composable
internal fun AppResetHost(model: AppResetViewModel, language: HomeLanguage, onBlocked: () -> Unit = {},
    content: @Composable () -> Unit) {
    val state by model.state.collectAsState()
    val blocked = state.blocked
    val question by model.confirmation.collectAsState()
    val labels = remember(language) { AppResetLabels(language) }
    // Idempotent quarantine also covers a reset racing with the first normal composition.
    SideEffect { if (blocked) onBlocked() }
    CompositionLocalProvider(LocalAppReset provides model) {
        if (!blocked) content()
        else LocalizedResources(language) {
            BackHandler { }
            Surface(Modifier.fillMaxSize().testTag("reset-startup-gate"),color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp,Alignment.CenterVertically)) {
                    when (state.phase) {
                        AppResetPhase.CHECKING -> { CircularProgressIndicator();Text(labels.checking()) }
                        AppResetPhase.COMMITTING -> { CircularProgressIndicator();Text(labels.preparing());Text(labels.locked()) }
                        AppResetPhase.WAITING_FOR_ANDROID -> { CircularProgressIndicator();Text(labels.waiting());Text(labels.locked()) }
                        else -> {
                            Text(labels.problem(state.problem));Text(labels.locked())
                            if (state.problem == AppResetProblem.JOURNAL_READ) AppButton(labels.retry(),model::retryCheck)
                            else AppButton(labels.resume(),model::resume,Modifier.testTag("reset-resume"))
                        }
                    }
                }
            }
        }
    }
    question?.let { LocalizedResources(language) { AppResetConfirmation(it,labels,model) } }
}

@Composable
internal fun AppResetAction(problem: SecureStorageFailure?, language: HomeLanguage) {
    val model = LocalAppReset.current ?: return
    if (!AppResetPolicy.mayOffer(problem)) return
    AppButton(AppResetLabels(language).open(),{ model.offer(problem) },Modifier.testTag("reset-open"))
}