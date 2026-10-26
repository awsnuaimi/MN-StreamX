package com.yourapp.iptv.ui.screen.backup

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.BackupUiState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class BackupScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var exported: Pair<String,String>? = null
    private var restored: BackupRestoreOptions? = null
    private var canceled = 0
    private fun show(state: BackupUiState = BackupUiState(), language: HomeLanguage = HomeLanguage.GERMAN) {
        compose.setContent {
            IPTVAppTheme(theme = HomeTheme.RED) {
                LocalizedResources(language) {
                    val mode = LocalInputModeManager.current
                    SideEffect { mode.requestInputMode(InputMode.Keyboard) }
                    Surface(Modifier.fillMaxSize()) {
                        BackupContent(state,BackupLabels(language),{},onCreate = { a,b -> exported = String(a) to String(b);a.fill('\u0000');b.fill('\u0000') },
                            onChoose = {},onInspect = { it.fill('\u0000') },onCancelPreview = { canceled++ },onRestore = { restored = it })
                    }
                }
            }
        }
    }
    @Test fun theExportFormPassesBothPasswordsOnlyOnExplicitActionAndThenClearsThem() {
        show()
        compose.onNodeWithTag("backup-password").performScrollTo().performTextReplacement("Synthetic-password-42")
        compose.onNodeWithTag("backup-confirmation").performScrollTo().performTextReplacement("Synthetic-password-42")
        compose.runOnIdle { assertNull(exported) }
        compose.onNodeWithTag("backup-create").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Synthetic-password-42" to "Synthetic-password-42",exported) }
        compose.onNodeWithTag("backup-password").performScrollTo().assertTextEquals("Passwort der Sicherung","")
    }
    @Test fun remoteDownMovesBetweenPasswordFieldsAndTheCreateButton() {
        show()
        compose.onNodeWithTag("backup-password").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("backup-password").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("backup-confirmation").assertIsFocused()
        compose.onNodeWithTag("backup-confirmation").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("backup-create").assertIsFocused()
        export("de-form")
    }
    @Test fun previewOptionsAreAppliedOnlyAfterConfirmingAndCancelDoesNotRestore() {
        val preview = BackupPreview("token",1000,listOf("Synthetic A"),1,0,3,4,5,6)
        show(BackupUiState(preview = preview))
        compose.runOnIdle { assertNull(restored) }
        compose.onNodeWithTag("backup-option-settings").performScrollTo().performClick()
        compose.onNodeWithTag("backup-option-folders").performScrollTo().performClick()
        compose.onNodeWithTag("backup-option-active").performScrollTo().performClick()
        compose.onNodeWithTag("backup-cancel").performClick()
        compose.runOnIdle { assertEquals(1,canceled); assertNull(restored) }
        compose.onNodeWithTag("backup-restore").performClick()
        compose.runOnIdle { assertEquals(BackupRestoreOptions(false,false,false),restored) }
    }
    @Test
    @Config(qualifiers = "w960dp-h540dp-mdpi")
    fun arabicPreviewKeepsTheRestoreAndCancelActionsVisibleOnASmallTv() {
        show(BackupUiState(preview = BackupPreview("token",1000,List(16) { "اشتراك تجريبي $it" },8,8,10,20,4,5)),HomeLanguage.ARABIC)
        compose.onNodeWithTag("backup-restore").assertIsDisplayed();compose.onNodeWithTag("backup-cancel").assertIsDisplayed()
        export("ar-preview")
    }
    @Test fun recoveryBlocksTheAppUntilReadyAndTheRestoreModalDoesNotDisposeNavigation() {
        val state = mutableStateOf(BackupMaintenance.STARTING)
        var mounted = 0;var disposed = 0;var retries = 0
        compose.setContent { IPTVAppTheme {
            BackupStartupGate(state.value,HomeLanguage.GERMAN,{ retries++ }) {
                DisposableEffect(Unit) { mounted++;onDispose { disposed++ } }
            }
        } }
        compose.runOnIdle { assertEquals(0,mounted);state.value = BackupMaintenance.READY }
        compose.runOnIdle { assertEquals(1,mounted);state.value = BackupMaintenance.RESTORING }
        compose.onNodeWithText("Daten werden wiederhergestellt. Bitte warten…").assertExists()
        compose.runOnIdle { assertEquals(0,disposed);state.value = BackupMaintenance.READY }
        compose.runOnIdle { assertEquals(1,mounted);assertEquals(0,disposed);state.value = BackupMaintenance.RECOVERY_REQUIRED }
        compose.onNodeWithText("Erneut versuchen").performClick()
        compose.runOnIdle { assertEquals(1,retries);assertEquals(1,disposed) }
    }
    private fun export(name: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        val bounds = compose.onNodeWithTag("backup-page",true).fetchSemanticsNode().boundsInRoot
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
                requireNotNull(owner(compose.activity.window.decorView)).draw(canvas)
                directory.resolve("backup-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            } finally { bitmap.recycle() }
        }
    }
}