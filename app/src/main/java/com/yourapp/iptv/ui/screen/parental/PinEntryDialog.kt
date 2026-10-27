package com.yourapp.iptv.ui.screen.parental

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.yourapp.iptv.ui.component.common.AppButton

/** PIN is transient Compose state only: no SavedStateHandle, IME, clipboard, logs or rememberSaveable. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun PinEntryDialog(title: String, labels: ParentalLabels, busy: Boolean,
    message: String, onDismiss: () -> Unit, onSubmit: (CharArray) -> Unit, canSubmit: Boolean = true) {
    var digits by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun append(value: String) { if (!busy && canSubmit && digits.length < 6) digits += value }
    Dialog(onDismissRequest = { if (!busy) { digits = ""; onDismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false,securePolicy = SecureFlagPolicy.SecureOn)) {
        val inputMode = LocalInputModeManager.current
        val window = LocalWindowInfo.current
        Surface(Modifier.widthIn(max = 440.dp).padding(12.dp),shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).testTag("parental-pin").onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount != 0 || busy) false
                else when (val code = event.nativeKeyEvent.keyCode) {
                    in AndroidKey.KEYCODE_0..AndroidKey.KEYCODE_9 -> { append((code - AndroidKey.KEYCODE_0).toString()); true }
                    in AndroidKey.KEYCODE_NUMPAD_0..AndroidKey.KEYCODE_NUMPAD_9 -> { append((code - AndroidKey.KEYCODE_NUMPAD_0).toString()); true }
                    AndroidKey.KEYCODE_DEL -> { if (canSubmit) digits = digits.dropLast(1); true }
                    else -> false
                }
            },horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title,style = MaterialTheme.typography.titleMedium)
                Text("●".repeat(digits.length) + "○".repeat(6 - digits.length),modifier = Modifier.testTag("pin-digits"))
                if (message.isNotEmpty()) Text(message,color = MaterialTheme.colorScheme.error,style = MaterialTheme.typography.bodySmall)
                listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf("clear","0","delete")).forEach { row ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            val label = when (key) { "clear" -> labels.clear(); "delete" -> labels.delete(); else -> key }
                            AppButton(label,{
                                when (key) { "clear" -> digits = ""; "delete" -> digits = digits.dropLast(1); else -> append(key) }
                            },Modifier.weight(1f).then(if (key == "1") Modifier.focusRequester(focus) else Modifier),enabled = !busy && canSubmit)
                        }
                    }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(labels.cancel(),{ digits = ""; onDismiss() },enabled = !busy)
                    AppButton(labels.confirm(),{ val secret = digits.toCharArray(); digits = ""; onSubmit(secret) },
                        enabled = !busy && canSubmit && digits.length == 6)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        LaunchedEffect(canSubmit,busy,window.isWindowFocused) {
            if (canSubmit && !busy && window.isWindowFocused) {
                inputMode.requestInputMode(InputMode.Keyboard); focus.requestFocus()
            }
        }
    }
}