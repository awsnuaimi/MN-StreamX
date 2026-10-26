package com.yourapp.iptv.ui.screen.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.testing.FakeSubscriptions
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.SettingsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class SubscriptionsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var used = 0
    private var returned = 0
    private fun screen(repository: FakeSubscriptions, language: HomeLanguage = HomeLanguage.GERMAN): SettingsViewModel {
        val model = SettingsViewModel(repository)
        compose.setContent {
            IPTVAppTheme(theme = HomeTheme.RED) {
                LocalizedResources(language) {
                    val mode = LocalInputModeManager.current
                    SideEffect { mode.requestInputMode(InputMode.Keyboard) }
                    Surface(Modifier.fillMaxSize().testTag("subscriptions-page")) {
                        SubscriptionsScreen({ returned++ }, { used++ }, model)
                    }
                }
            }
        }
        return model
    }
    @Test fun addingAndSavingThroughTheActualFormKeepsTheOldAccountAndActivatesOnlyAfterSaving() {
        val repository = FakeSubscriptions().apply { latency = 0 }
        screen(repository)
        compose.onNodeWithTag("subscription-add").performScrollTo().performClick()
        compose.onNodeWithTag("subscription-name").performScrollTo().performTextReplacement("Second")
        compose.onNode(hasSetTextAction() and hasText("M3U-Playlist-URL"))
            .performScrollTo().performTextReplacement(FakeSubscriptions.account("B").m3uUrl)
        compose.runOnIdle { assertEquals(Subscriptions.DEFAULT_ID, repository.catalog.value.activeId); assertEquals(0, repository.saves) }
        export("red-add")
        compose.onNodeWithTag("subscription-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, repository.catalog.value.entries.size)
            assertEquals("Second", repository.catalog.value.active.name)
            assertEquals(1, used)
        }
    }
    @Test fun theSwitchDialogCanCancelOrExplicitlyDiscardAnUnsavedDraft() {
        val repository = FakeSubscriptions().apply { latency = 0 }
        runBlocking { repository.saveSubscription(null, "Second", FakeSubscriptions.account("B")) }
        val second = repository.catalog.value.activeId
        val model = screen(repository)
        compose.onNodeWithTag("subscription-remove-$second").assertDoesNotExist()
        compose.onNodeWithTag("subscription-name").performScrollTo().performTextReplacement("Unfinished")
        compose.onNodeWithTag("subscription-use-default").performScrollTo().performClick()
        compose.runOnIdle { assertNotNull(model.uiState.value.pending) }
        compose.onNodeWithTag("subscription-confirm").assertTextContains("Fortfahren")
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertEquals(second, repository.catalog.value.activeId); assertTrue(model.uiState.value.dirty) }
        compose.onNodeWithTag("subscription-use-default").performScrollTo().performClick()
        compose.onNodeWithTag("subscription-confirm").performClick()
        compose.runOnIdle { assertEquals(Subscriptions.DEFAULT_ID, repository.catalog.value.activeId); assertEquals(1, used) }
        export("red-switch")
    }
    @Test
    @Config(qualifiers = "w960dp-h540dp-mdpi")
    fun arabicSmallTvLayoutKeepsTheAddAndNameControlsReachableByRemoteFocus() {
        screen(FakeSubscriptions().apply { latency = 0 }, HomeLanguage.ARABIC)
        compose.onNodeWithTag("subscription-add").performScrollTo().performClick()
        compose.onNodeWithTag("subscription-add").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("subscription-add").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("subscription-name").assertIsFocused()
        compose.onNodeWithTag("subscription-save").performScrollTo().assertIsEnabled()
        export("red-arabic")
    }
    @Test fun leavingADirtyFormUsesLocalizedConfirmationAndDoesNotSaveTheDraft() {
        val repository = FakeSubscriptions().apply { latency = 0 }
        val model = screen(repository)
        compose.onNodeWithTag("subscription-name").performScrollTo().performTextReplacement("Draft")
        compose.onNodeWithText("Zurück").performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.runOnIdle { assertEquals(0, returned); assertTrue(model.uiState.value.dirty) }
        compose.onNodeWithText("Zurück").performClick()
        compose.onNodeWithText("Fortfahren").performClick()
        compose.runOnIdle { assertEquals(1, returned); assertEquals("Original", repository.catalog.value.active.name) }
    }

    private fun export(name: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        val bounds = compose.onNodeWithTag("subscriptions-page", true).fetchSemanticsNode().boundsInRoot
        fun owner(view: View): View? {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
            if (view is ViewGroup) for (i in 0 until view.childCount) owner(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.runOnIdle {
            directory.mkdirs()
            val bitmap = Bitmap.createBitmap(bounds.width.toInt(), bounds.height.toInt(), Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap); canvas.translate(-bounds.left, -bounds.top)
                requireNotNull(owner(compose.activity.window.decorView)).draw(canvas)
                directory.resolve("subscriptions-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}