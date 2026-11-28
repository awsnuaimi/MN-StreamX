@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package mnstudio.apps.mnstreamx.ui.screen.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.flow.collect
import mnstudio.apps.mnstreamx.R

/** TV navigation is never equivalent to requesting the soft keyboard. Press OK to edit. */
internal enum class CaptionTvEntryAction { NONE, EDIT, FINISH }

internal fun captionTvEntryAction(editing: Boolean, keyCode: Int, action: Int): CaptionTvEntryAction {
    if (action != KeyEvent.ACTION_DOWN) return CaptionTvEntryAction.NONE
    if (editing && keyCode in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE))
        return CaptionTvEntryAction.FINISH
    if (!editing && keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER)) return CaptionTvEntryAction.EDIT
    return CaptionTvEntryAction.NONE
}

/** Field stays focusable while read-only, but the IME is only summoned after an explicit OK. */
@Composable
internal fun CaptionTvEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false
) {
    var editing by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    fun finishEditing() {
        editing = false
        keyboard?.hide()
    }

    // The IME may consume BACK before Compose sees it. When it vanishes, exit edit mode
    // so that merely revisiting this field with DPAD does not reopen the TV keyboard.
    LaunchedEffect(editing, density, imeInsets) {
        if (!editing) return@LaunchedEffect
        var wasVisible = false
        snapshotFlow { imeInsets.getBottom(density) > 0 }.collect { visible ->
            if (visible) wasVisible = true
            else if (wasVisible) finishEditing()
        }
    }
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            keyboard?.show()
        }
    }
    BackHandler(enabled = editing) { finishEditing() }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = !editing,
            modifier = modifier.fillMaxWidth()
                .onFocusChanged { if (!it.hasFocus && editing) finishEditing() }
                .onPreviewKeyEvent { event ->
                    when (captionTvEntryAction(editing, event.nativeKeyEvent.keyCode,
                            event.nativeKeyEvent.action)) {
                        CaptionTvEntryAction.EDIT -> { editing = true; true }
                        CaptionTvEntryAction.FINISH -> { finishEditing(); true }
                        CaptionTvEntryAction.NONE -> false
                    }
                },
            label = { Text(label) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            visualTransformation = if (secret && !revealed) PasswordVisualTransformation()
                else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (secret && !revealed) KeyboardType.Password else KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { finishEditing() }),
            supportingText = { Text(stringResource(if (editing) R.string.caption_input_finish_hint
                else R.string.caption_input_edit_hint)) }
        )
        // Keep outside the field's preview handler so OK toggles visibility.
        if (secret) {
            TextButton(onClick = { finishEditing(); revealed = !revealed }) {
                Text(stringResource(if (revealed) R.string.caption_key_hide else R.string.caption_key_show))
            }
        }
    }
}