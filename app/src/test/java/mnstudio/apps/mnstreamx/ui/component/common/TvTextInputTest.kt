package mnstudio.apps.mnstreamx.ui.component.common

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import mnstudio.apps.mnstreamx.domain.model.HomeLanguage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class TvTextInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var tick by mutableIntStateOf(0)
    private var key by mutableStateOf("AbC-19_O0.I1")
    private var applies = 0
    private val ime get() = shadowOf(compose.activity.getSystemService(InputMethodManager::class.java))
    private fun board(enabled: Boolean = true, language: HomeLanguage = HomeLanguage.GERMAN) {
        compose.setContent {
            MaterialTheme {
                LocalizedResources(language) {
                    val editor = rememberTvTextInputState()
                    val mode = LocalInputModeManager.current
                    SideEffect { mode.requestInputMode(InputMode.Keyboard) }
                    Column(Modifier.fillMaxSize()) {
                        Text("refresh $tick")
                        TvTextInput(editor, "License", key, { key = it; applies++ },
                            Modifier.testTag("license"), enabled = enabled, secret = true, maxLength = 16)
                        TvTextInput(editor, "Region", "germanywestcentral", {}, Modifier.testTag("region"), keyboardType = KeyboardType.Ascii)
                    }
                    TvTextInputDialog(editor)
                }
            }
        }
        compose.runOnIdle { compose.activity.window.decorView.dispatchWindowFocusChanged(true) }
        if (enabled) compose.onNodeWithTag("license").performSemanticsAction(SemanticsActions.RequestFocus)
    }
    private fun open() {
        compose.onNodeWithTag("license").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { ShadowDialog.getLatestDialog().window!!.decorView.dispatchWindowFocusChanged(true) }
        compose.waitUntil { runCatching { compose.onNodeWithTag("tv-input-editor").assertIsFocused() }.isSuccess }
    }
    private fun editor() = compose.onNodeWithTag("tv-input-editor")
    private fun layout(): TextLayoutResult {
        val values = mutableListOf<TextLayoutResult>()
        editor().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(values) }
        return values.single()
    }
    private fun imeInsets(visible: Boolean) {
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            val target = requireNotNull(owner(ShadowDialog.getLatestDialog().window!!.decorView))
            ViewCompat.dispatchApplyWindowInsets(target, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), if (visible) Insets.of(0, 0, 0, 240) else Insets.NONE)
                .setVisible(WindowInsetsCompat.Type.ime(), visible).build())
        }
        compose.waitForIdle()
    }
    @Test fun arrowsNavigateWithoutCreatingAnEditableConnection() {
        board()
        compose.onNodeWithTag("license").assertIsFocused().assert(!hasSetTextAction())
        compose.onNodeWithTag("license").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("region").assertIsFocused().assert(!hasSetTextAction())
        compose.onNodeWithTag("tv-input-dialog").assertDoesNotExist()
        assertFalse(ime.isSoftInputVisible)
    }
    @Test fun visibilitySwitchShowsTheExactCharactersWithoutApplyingThem() {
        board(); open()
        assertNotEquals(key, layout().layoutInput.text.text)
        compose.onNodeWithTag("tv-input-visibility").performClick()
        assertEquals(key, layout().layoutInput.text.text)
        compose.onNodeWithTag("tv-input-visibility").performClick()
        assertNotEquals(key, layout().layoutInput.text.text)
        assertEquals(0, applies)
    }
    @Test fun revealKeepsCursorPositionAndMixedCaseInput() {
        board(); open()
        editor().performTextReplacement("a1b2")
        editor().performTextInputSelection(TextRange(2))
        compose.onNodeWithTag("tv-input-visibility").performClick()
        editor().performTextInput("Z_")
        compose.onNodeWithTag("tv-input-apply").performClick()
        assertEquals("a1Z_b2", key)
        assertEquals(1, applies)
    }
    @Test fun backCancelsTheDraftAndParentUpdatesDoNotReopenIt() {
        board(); open()
        editor().performTextReplacement("cancelled")
        compose.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        repeat(10) { compose.runOnIdle { tick++ } }
        compose.onNodeWithTag("tv-input-dialog").assertDoesNotExist()
        assertEquals("AbC-19_O0.I1", key)
        assertEquals(0, applies)
    }
    @Test fun imeDismissStopsTypingUntilTheUserExplicitlyOpensItAgain() {
        board(); open()
        compose.mainClock.advanceTimeBy(100)
        imeInsets(true)
        compose.onNodeWithTag("tv-input-apply").assertIsDisplayed()
        compose.onNodeWithTag("tv-input-cancel").assertIsDisplayed()
        compose.runOnIdle { ShadowDialog.getLatestDialog().window!!.decorView.dispatchWindowFocusChanged(false) }
        val shown = mutableListOf<Boolean>()
        ime.setSoftInputVisibilityHandler { shown += it }
        compose.runOnIdle {
            val manager = compose.activity.getSystemService(InputMethodManager::class.java)
            manager.showSoftInput(compose.activity.window.decorView, 0)
            manager.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        imeInsets(false)
        compose.runOnIdle { ShadowDialog.getLatestDialog().window!!.decorView.dispatchWindowFocusChanged(true) }
        shown.clear()
        repeat(10) { compose.runOnIdle { tick++ } }
        editor().assertIsDisplayed().assert(!hasSetTextAction())
        assertFalse(ime.isSoftInputVisible)
        compose.onNodeWithTag("tv-input-keyboard").assertIsFocused()
        assertFalse(shown.contains(true))
        compose.onNodeWithTag("tv-input-keyboard").performClick()
        editor().assert(hasSetTextAction())
    }
    @Test fun explicitKeyboardCloseKeepsTheDraftAndDoesNotNeedAnImeVisibilityEvent() {
        board(); open()
        editor().performTextReplacement("AbC-19")
        compose.onNodeWithTag("tv-input-keyboard").performClick()
        editor().assert(!hasSetTextAction())
        repeat(5) { compose.runOnIdle { tick++ } }
        compose.onNodeWithTag("tv-input-dialog").assertIsDisplayed()
        compose.onNodeWithTag("tv-input-apply").performClick()
        assertEquals("AbC-19", key)
        assertEquals(1, applies)
    }
    @Test fun doneAppliesOnceAndPreservesCaseAndSpaces() {
        board(); open()
        editor().performTextReplacement(" AbC-19_O0 ")
        editor().performImeAction()
        compose.onNodeWithTag("tv-input-dialog").assertDoesNotExist()
        assertEquals(" AbC-19_O0 ", key)
        assertEquals(1, applies)
    }
    @Test fun pasteRespectsTheExistingLimitAndReportsCharacterCount() {
        board(); open()
        editor().performTextReplacement("0123456789abcdefghijkl")
        compose.onNodeWithTag("tv-input-count").assertTextEquals("16 Zeichen")
        compose.onNodeWithTag("tv-input-apply").performClick()
        assertEquals("0123456789abcdef", key)
    }
    @Test fun disabledFieldCannotOpenAnEditor() {
        board(enabled = false)
        compose.onNodeWithTag("license").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("tv-input-dialog").assertDoesNotExist()
    }
    @Test fun heldOkDoesNotApplyOrOpenAnotherDialog() {
        board()
        compose.onNodeWithTag("license").performKeyInput { keyDown(Key.Enter); advanceEventTime(500); keyUp(Key.Enter) }
        compose.onAllNodesWithTag("tv-input-dialog").assertCountEquals(1)
        assertEquals(0, applies)
    }
    @Test fun arabicUiKeepsRevealedTechnicalCharactersLeftToRight() {
        board(language = HomeLanguage.ARABIC); open()
        compose.onNodeWithTag("tv-input-visibility").performClick()
        assertEquals(key, layout().layoutInput.text.text)
        assertEquals(TextDirection.Ltr, layout().layoutInput.style.textDirection)
    }
    @Test
    @Config(qualifiers = "w320dp-h720dp-mdpi")
    fun narrowDialogKeepsVisibilityAndCancelReachable() {
        board(language = HomeLanguage.ENGLISH); open()
        compose.onNodeWithTag("tv-input-visibility").performScrollTo().performClick()
        compose.onNodeWithTag("tv-input-cancel").performClick()
        compose.onNodeWithTag("tv-input-dialog").assertDoesNotExist()
        assertEquals(0, applies)
    }
}