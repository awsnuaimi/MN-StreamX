package com.yourapp.iptv.ui.screen.parental

import android.app.Application
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class,ExperimentalTestApi::class)
class PinEntryDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var submitted: CharArray? = null
    private var cancelled = 0
    private fun show(language: HomeLanguage = HomeLanguage.GERMAN, canSubmit: Boolean = true) {
        compose.setContent { IPTVAppTheme(theme = HomeTheme.RED) { LocalizedResources(language) {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            val labels = ParentalLabels(language)
            PinEntryDialog(labels.pin(),labels,false,"",{ cancelled++ },{ submitted = it },canSubmit)
        } } }
        // A real Dialog receives this native window event; Robolectric does not send it automatically.
        compose.runOnIdle { ShadowDialog.getLatestDialog().window!!.decorView.dispatchWindowFocusChanged(true) }
        compose.waitForIdle()
    }
    @Test fun onlyMaskedDigitsAreExposedAndTheWholePinIsSubmittedOnce() {
        show();compose.onNodeWithText("Bestätigen").assertIsNotEnabled()
        (1..6).forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.onNodeWithText("123456").assertDoesNotExist()
        compose.onNodeWithText("●●●●●●").assertIsDisplayed()
        compose.onNodeWithText("Bestätigen").performClick()
        compose.runOnIdle { assertArrayEquals("123456".toCharArray(),submitted) }
        compose.onNodeWithText("Bestätigen").assertIsNotEnabled()
    }
    @Test fun remoteNumberKeysAndDeletionDoNotRequireAnOnScreenKeyboard() {
        show();compose.onNodeWithText("1").assertIsFocused();compose.onNodeWithTag("parental-pin").performKeyInput {
            pressKey(Key.One);pressKey(Key.Two);pressKey(Key.Three)
        }
        compose.onNodeWithText("●●●○○○").assertExists()
        compose.onNodeWithText("Ziffer löschen").performClick();compose.onNodeWithText("●●○○○○").assertExists()
        compose.onNodeWithText("Leeren").performClick();compose.onNodeWithText("○○○○○○").assertExists()
    }
    @Test fun cooldownKeepsCancelUsableAndDoesNotAcceptMoreGuesses() {
        show(canSubmit = false)
        compose.onNodeWithText("1").assertIsNotEnabled();compose.onNodeWithText("Bestätigen").assertIsNotEnabled()
        compose.onNodeWithText("Abbrechen").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,cancelled);assertNull(submitted) }
    }
    @Test fun arabicPadKeepsThePhysicalNumberOrderAndPrimaryActionsVisibleOnASmallTv() {
        show(HomeLanguage.ARABIC)
        compose.onNodeWithText("1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("1").assertIsFocused()
        compose.onNodeWithText("1").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("2").assertIsFocused()
        compose.onNodeWithText("تأكيد").assertIsDisplayed();compose.onNodeWithText("إلغاء").assertIsDisplayed()
        export("ar-pin")
    }
    @Test fun pinDialogUsesASecureWindowEvenBeforeProtectionIsConfigured() {
        show()
        compose.runOnIdle {
            val window = requireNotNull(ShadowDialog.getLatestDialog().window)
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
    }
    private fun export(name: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        val bounds = compose.onNodeWithTag("parental-pin",true).fetchSemanticsNode().boundsInRoot
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            directory.mkdirs()
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(),bounds.height.toInt(),Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap);canvas.translate(-bounds.left,-bounds.top)
                requireNotNull(owner(ShadowDialog.getLatestDialog().window!!.decorView)).draw(canvas)
                directory.resolve("parental-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            } finally { bitmap.recycle() }
        }
    }
}