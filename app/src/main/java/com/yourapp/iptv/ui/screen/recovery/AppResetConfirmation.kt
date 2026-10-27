package com.yourapp.iptv.ui.screen.recovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.yourapp.iptv.ui.component.common.AppButton
import com.yourapp.iptv.viewmodel.AppResetViewModel
import com.yourapp.iptv.viewmodel.ResetConfirmation

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AppResetConfirmation(question: ResetConfirmation, labels: AppResetLabels, model: AppResetViewModel) {
    val cancel = remember { FocusRequester() }
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    Dialog(onDismissRequest = model::cancel,properties = DialogProperties(usePlatformDefaultWidth = false,
        securePolicy = SecureFlagPolicy.SecureOn)) {
        val window = LocalWindowInfo.current;val mode = LocalInputModeManager.current
        Surface(Modifier.fillMaxWidth(0.88f).heightIn(max = height),shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).testTag("reset-confirmation"),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (question.finalStep) labels.finalTitle() else labels.title(),style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f,fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (question.finalStep) Text(labels.finalNote())
                    Text(labels.scope());Text(labels.outside());Text(labels.noRecovery())
                    if (!question.finalStep) Row(Modifier.fillMaxWidth().testTag("reset-acknowledge")
                        .toggleable(question.acknowledged,role = Role.Checkbox,onValueChange = model::acknowledge),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(question.acknowledged,onCheckedChange = null)
                        Text(labels.acknowledge(),Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppButton(labels.cancel(),model::cancel,Modifier.focusRequester(cancel).testTag("reset-cancel"))
                    if (question.finalStep) AppButton(labels.erase(),model::confirm,Modifier.testTag("reset-erase"))
                    else AppButton(labels.review(),model::review,Modifier.testTag("reset-review"),enabled = question.acknowledged)
                }
            }
        }
        LaunchedEffect(question.finalStep,window.isWindowFocused) {
            if (window.isWindowFocused) { mode.requestInputMode(InputMode.Keyboard);withFrameNanos { };cancel.requestFocus() }
        }
    }
}