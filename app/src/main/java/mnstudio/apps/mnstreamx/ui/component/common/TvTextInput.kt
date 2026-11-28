@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package mnstudio.apps.mnstreamx.ui.component.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mnstudio.apps.mnstreamx.R

internal class TvTextInputRequest(val label: String, val value: String, val secret: Boolean,
    val keyboardType: KeyboardType, val maxLength: Int, val onApply: (String) -> Unit) {
    override fun toString() = "TvTextInputRequest(secret=$secret)"
}

internal class TvTextInputState {
    var request by mutableStateOf<TvTextInputRequest?>(null)
        private set
    fun open(value: TvTextInputRequest) { if (request == null) request = value }
    fun dismiss() { request = null }
    fun apply(expected: TvTextInputRequest, value: String) {
        if (request !== expected) return
        request = null
        expected.onApply(value)
    }
}

@Composable
internal fun rememberTvTextInputState(): TvTextInputState = remember { TvTextInputState() }

/** A returning window/IME cannot turn navigation back into typing. */
internal class TvTextInputSession {
    var editing by mutableStateOf(true)
        private set
    private var imeWasVisible = false
    fun beginEditing() { editing = true; imeWasVisible = false }
    fun stopEditing() { editing = false; imeWasVisible = false }
    fun imeVisibilityChanged(visible: Boolean) {
        if (visible && editing) imeWasVisible = true
        else if (!visible && imeWasVisible) stopEditing()
    }
}

/** Focus alone never creates a text input connection. */
@Composable
internal fun TvTextInput(editor: TvTextInputState, label: String, value: String,
    onValueChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    secret: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "", maxLength: Int = Int.MAX_VALUE) {
    val technical = secret || keyboardType == KeyboardType.Uri || keyboardType == KeyboardType.Ascii
    val preview = if (secret && value.isNotEmpty()) "••••••••" else value.ifEmpty { placeholder.ifEmpty { "—" } }
    Surface(onClick = { editor.open(TvTextInputRequest(label, value, secret, keyboardType, maxLength, onValueChange)) },
        enabled = enabled, modifier = modifier.fillMaxWidth().tvFocusIndicator(),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = if (technical) TextDirection.Ltr else TextDirection.Content))
            Text("OK · " + stringResource(R.string.tv_input_edit), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvTextInputDialog(editor: TvTextInputState, enabled: Boolean = true) {
    val request = editor.request ?: return
    var draft by remember(request) { mutableStateOf(TextFieldValue(request.value, TextRange(request.value.length))) }
    var revealed by remember(request) { mutableStateOf(false) }
    val session = remember(request) { TvTextInputSession() }
    val input = remember(request) { FocusRequester() }
    val keyboardAction = remember(request) { FocusRequester() }
    val initialEpoch = remember(request) { FocusInputEpoch.value }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keyboard = remember(request) { mutableStateOf<SoftwareKeyboardController?>(null) }
    val scope = rememberCoroutineScope()
    val applyLabel = stringResource(R.string.tv_input_apply)
    val cancelLabel = stringResource(R.string.tv_input_cancel)
    val visibilityLabel = stringResource(if (revealed) R.string.tv_input_hide else R.string.tv_input_show)
    val keyboardLabel = stringResource(if (session.editing) R.string.tv_input_keyboard_close else R.string.tv_input_keyboard)
    val countLabel = stringResource(R.string.tv_input_characters, draft.text.length)
    fun cancel() { keyboard.value?.hide(); editor.dismiss() }
    fun apply() { if (enabled) { keyboard.value?.hide(); editor.apply(request, draft.text) } }
    DisposableEffect(request, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                revealed = false
                session.stopEditing()
                keyboard.value?.hide()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AlertDialog(onDismissRequest = ::cancel, modifier = Modifier.imePadding().testTag("tv-input-dialog"),
        title = { Text(request.label) },
        text = {
            val window = LocalWindowInfo.current
            val inputMode = LocalInputModeManager.current
            val dialogKeyboard = LocalSoftwareKeyboardController.current
            val imeVisible = WindowInsets.isImeVisible
            SideEffect { keyboard.value = dialogKeyboard; session.imeVisibilityChanged(imeVisible) }
            LaunchedEffect(request) {
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                snapshotFlow { window.isWindowFocused }.first { it }
                withFrameNanos { }
                if (FocusInputEpoch.value == initialEpoch && session.editing) input.requestFocus()
            }
            LaunchedEffect(request, session.editing) {
                if (!session.editing) {
                    val epoch = FocusInputEpoch.value
                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    snapshotFlow { window.isWindowFocused }.first { it }
                    withFrameNanos { }
                    if (editor.request === request && epoch == FocusInputEpoch.value && window.isWindowFocused &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        inputMode.requestInputMode(InputMode.Keyboard)
                        keyboardAction.requestFocus()
                    }
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val technical = request.secret || request.keyboardType == KeyboardType.Uri || request.keyboardType == KeyboardType.Ascii
                OutlinedTextField(value = draft, onValueChange = { value ->
                    val text = value.text.take(request.maxLength)
                    draft = value.copy(text = text, selection = TextRange(
                        value.selection.start.coerceAtMost(text.length), value.selection.end.coerceAtMost(text.length)))
                }, modifier = Modifier.fillMaxWidth().focusRequester(input).testTag("tv-input-editor"),
                    enabled = enabled, readOnly = !session.editing, singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        textDirection = if (technical) TextDirection.Ltr else TextDirection.Content,
                        fontFamily = if (request.secret) FontFamily.Monospace else LocalTextStyle.current.fontFamily),
                    visualTransformation = if (request.secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                        keyboardType = if (request.secret) KeyboardType.Password else request.keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { apply() }))
                Text(countLabel, modifier = Modifier.testTag("tv-input-count"), style = MaterialTheme.typography.bodySmall)
                if (request.secret) TextButton(onClick = { revealed = !revealed },
                    modifier = Modifier.testTag("tv-input-visibility")) { Text(visibilityLabel) }
                TextButton(onClick = {
                    if (session.editing) { session.stopEditing(); keyboard.value?.hide() }
                    else {
                        session.beginEditing()
                        scope.launch {
                            withFrameNanos { }
                            if (editor.request === request && session.editing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                input.requestFocus()
                                keyboard.value?.show()
                            }
                        }
                    }
                }, enabled = enabled, modifier = Modifier.focusRequester(keyboardAction).testTag("tv-input-keyboard")) { Text(keyboardLabel) }
            }
        },
        confirmButton = { TextButton(onClick = ::apply, enabled = enabled,
            modifier = Modifier.testTag("tv-input-apply")) { Text(applyLabel) } },
        dismissButton = { TextButton(onClick = ::cancel, modifier = Modifier.testTag("tv-input-cancel")) { Text(cancelLabel) } })
}