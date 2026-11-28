package mnstudio.apps.mnstreamx.ui.screen.player

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.*
import mnstudio.apps.mnstreamx.ui.component.common.LocalizedResources
import mnstudio.apps.mnstreamx.ui.theme.IPTVAppTheme
import mnstudio.apps.mnstreamx.viewmodel.CaptionViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class CaptionInputDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private var saved: CloudCredentials? = null
    @After fun clear() { compose.runOnIdle { store.clear() } }
    private fun screen() {
        lateinit var model: CaptionViewModel
        compose.runOnUiThread {
            model = CaptionViewModel(object : CaptionRepository {
                private val settings = MutableStateFlow(CaptionSettings())
                override fun observe() = settings
                override suspend fun save(value: CaptionSettings) { settings.value = value }
            }, Dispatchers.IO, object : CaptionModels {
                override suspend fun prepare(progress: (Int) -> Unit): File = error("No model should be loaded")
            }, SpeechEngineFactory { error("No inference should be started") },
                CaptionTranslatorFactory { error("No translator should be started") }, CaptionDevice { true },
                object : CloudCredentialVault {
                    override suspend fun read(): CloudCredentials? = null
                    override suspend fun save(value: CloudCredentials) { saved = value }
                    override suspend fun clear() { saved = null }
                }, CloudCaptionSessionFactory { _, _, _ -> error("No cloud session should be opened") })
            store.put("captions", model)
        }
        compose.setContent {
            IPTVAppTheme(theme = HomeTheme.RED) {
                LocalizedResources(HomeLanguage.GERMAN) {
                    val mode = LocalInputModeManager.current
                    SideEffect { mode.requestInputMode(InputMode.Keyboard) }
                    Surface(Modifier.fillMaxSize()) { PlayerCaptionDialog(model, null) {} }
                }
            }
        }
        focusWindow()
        compose.waitUntil { model.uiState.value.loaded }
    }
    private fun focusWindow() {
        compose.runOnIdle { ShadowDialog.getLatestDialog().window!!.decorView.dispatchWindowFocusChanged(true) }
    }
    private fun field(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("caption-options").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    @Test fun keyAndRegionAreNavigationControlsUntilExplicitlySelected() {
        screen()
        field("caption-cloud-key").assertIsDisplayed().assert(!hasSetTextAction())
        field("caption-cloud-region").assertIsDisplayed().assert(!hasSetTextAction())
        compose.onNodeWithTag("tv-input-dialog").assertDoesNotExist()
        assertNull(saved)
    }
    @Test fun applyingTheEditorDoesNotPersistUntilTheExistingSaveButtonIsPressed() {
        screen()
        val key = "0123456789abcdef0123456789abcdef"
        field("caption-cloud-key").performClick()
        focusWindow()
        compose.waitUntil { runCatching { compose.onNodeWithTag("tv-input-editor").assertIsFocused() }.isSuccess }
        compose.onNodeWithTag("tv-input-editor").performTextReplacement(key)
        compose.onNodeWithTag("tv-input-visibility").performClick()
        compose.onNodeWithTag("tv-input-apply").performClick()
        assertNull(saved)
        field("caption-cloud-save").performClick()
        compose.waitUntil { saved != null }
        assertEquals(key, saved!!.key)
        assertEquals("germanywestcentral", saved!!.region)
        compose.onNodeWithTag("tv-input-dialog").assertDoesNotExist()
    }
}