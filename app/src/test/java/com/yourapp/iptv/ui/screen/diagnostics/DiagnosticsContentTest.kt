package com.yourapp.iptv.ui.screen.diagnostics

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
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.testing.diagnosticSnapshot
import com.yourapp.iptv.ui.component.common.LocalizedResources
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import com.yourapp.iptv.viewmodel.DiagnosticsState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class,qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class,ExperimentalTestApi::class)
class DiagnosticsContentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf(DiagnosticsState(snapshot = diagnosticSnapshot(),source = DiagnosticSource.XTREAM,
        sourceKey = "a".repeat(64),sourceReady = true,checks = listOf(DiagnosticCheck(DiagnosticArea.ACCOUNT,DiagnosticStatus.PASS,DiagnosticIssue.OK,200))))
    private var runs = 0;private var shares = 0;private var clears = 0;private var cancelled = 0
    private fun show(language: HomeLanguage = HomeLanguage.GERMAN, events: List<DiagnosticEvent> = emptyList()) {
        compose.setContent { IPTVAppTheme(theme = HomeTheme.RED) { LocalizedResources(language) {
            val input = LocalInputModeManager.current
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            Surface(Modifier.fillMaxSize()) { DiagnosticsContent(state.value,events,DiagnosticText(language),{},
                { runs++ },{ cancelled++;state.value = state.value.copy(busy = false) },{ shares++ },{ clears++ }) }
        } } }
    }
    @Test fun networkChecksAndReportSharingAreSeparateExplicitActions() {
        show();compose.runOnIdle { assertEquals(0,runs);assertEquals(0,shares) }
        compose.onNodeWithTag("diagnostics-start").performClick();compose.onNodeWithTag("diagnostics-share").performClick()
        compose.onNodeWithTag("diagnostics-clear").performClick()
        compose.runOnIdle { assertEquals(1,runs);assertEquals(1,shares);assertEquals(1,clears) }
    }
    @Test fun remoteFocusMovesToCancelWhileBusyAndReturnsToRunAfterCancellation() {
        show()
        compose.onNodeWithTag("diagnostics-start").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("diagnostics-start").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("diagnostics-share").assertIsFocused()
        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        compose.onNodeWithTag("diagnostics-cancel").assertIsFocused();compose.onNodeWithTag("diagnostics-share").assertIsNotEnabled()
        compose.onNodeWithTag("diagnostics-cancel").performClick();compose.onNodeWithTag("diagnostics-start").assertIsFocused()
        compose.runOnIdle { assertEquals(1,cancelled) }
    }
    @Test fun germanMetricsAndPrimaryActionsFitASmallTv() {
        show()
        listOf("diagnostics-back","diagnostics-start","diagnostics-share","diagnostics-clear").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        compose.onNodeWithText("Fehlerdiagnose").assertExists();export("de-small")
    }
    @Test fun arabicKeepsActionsVisibleAndTheLastBoundedSessionEventReachable() {
        val events = (64L downTo 1L).map { DiagnosticEvent(it,1000,DiagnosticArea.PLAYBACK,DiagnosticIssue.DECODER,"a".repeat(64),4005) }
        show(HomeLanguage.ARABIC,events)
        listOf("diagnostics-start","diagnostics-share","diagnostics-clear").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        export("ar-small")
        compose.onNodeWithTag("diagnostics-list").performScrollToNode(hasTestTag("diagnostics-event-1"))
        compose.onNodeWithTag("diagnostics-event-1").assertIsDisplayed()
        compose.onNodeWithText("a".repeat(64),substring = true).assertDoesNotExist()
    }
    @Test fun unavailableSnapshotAndSharingFailureAreClearlyPresentedWithoutOpeningAnotherApp() {
        state.value = DiagnosticsState(sourceReady = true,shareFailure = true)
        show();compose.onNodeWithTag("diagnostics-share").assertIsNotEnabled()
        compose.onNodeWithText(DiagnosticText(HomeLanguage.GERMAN).shareFailed()).assertExists()
        compose.runOnIdle { assertEquals(0,shares) }
    }
    private fun export(name: String) {
        val directory = System.getProperty("mnstreamx.uiPreviewDir")?.let(::File) ?: return
        val bounds = compose.onNodeWithTag("diagnostics-page",true).fetchSemanticsNode().boundsInRoot
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
                directory.resolve("diagnostics-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            } finally { bitmap.recycle() }
        }
    }
}