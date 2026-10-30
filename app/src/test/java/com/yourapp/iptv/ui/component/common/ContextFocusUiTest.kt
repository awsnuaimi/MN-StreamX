package com.yourapp.iptv.ui.component.common

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yourapp.iptv.testing.waitForFocus
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class ContextFocusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var manager: FocusManager
    private lateinit var focus: ContextFocus
    private var ready by mutableStateOf(false)
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private fun board() {
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            manager = LocalFocusManager.current
            val mode = LocalInputModeManager.current
            SideEffect { mode.requestInputMode(InputMode.Keyboard) }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                focus = rememberContextFocus("owner")
                focus.Resume(setOf("controls", "results"), "controls", "query")
                focus.Restore("controls", listOf("query"))
                focus.Restore("results", listOf("result"), ready)
                Column(focus.inputModifier()) {
                    Button({}, focus.target("controls", "query", 0).testTag("query")) { Text("Query") }
                    if (ready) Button({}, focus.target("results", "result", 0).testTag("result")) { Text("Result") }
                }
            }
        }
    }
    @Test fun restorationWaitsForCompositionRatherThanRequestingADetachedNode() {
        board(); compose.runOnIdle { focus.request("results") }
        compose.onNodeWithTag("query").assertIsFocused()
        compose.runOnIdle { ready = true }
        compose.waitForFocus("result")
    }
    @Test fun aFreshArrowCancelsRestorationBeforeTheDelayedResultArrives() {
        board(); compose.runOnIdle { focus.request("results") }
        compose.onNodeWithTag("query").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { ready = true }
        compose.onNodeWithTag("query").assertIsFocused()
        compose.runOnIdle { assertNull(focus.pending) }
    }
    @Test fun stopCancelsOldRequestsAndResumeRecallsTheLastFocusedControl() {
        ready = true; board()
        compose.onNodeWithTag("result").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { focus.request("controls"); owner.registry.currentState = Lifecycle.State.CREATED; manager.clearFocus(force = true) }
        compose.onNodeWithTag("result").assertIsNotFocused()
        compose.runOnIdle { assertNull(focus.pending) }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForFocus("result")
    }
    @Test fun aPointerPressAlsoCancelsTheDelayedRequest() {
        board(); compose.runOnIdle { focus.request("results") }
        compose.onNodeWithTag("query").performTouchInput { click() }
        compose.runOnIdle { ready = true }
        compose.onNodeWithTag("result").assertIsNotFocused()
        compose.runOnIdle { assertNull(focus.pending) }
    }
    @Test fun activityInputCancelsARequestEvenWithoutAFocusedChild() {
        board()
        compose.runOnIdle { focus.request("results"); manager.clearFocus(force = true) }
        compose.runOnIdle { FocusInputEpoch.advance(); ready = true }
        compose.onNodeWithTag("result").assertIsNotFocused()
        compose.runOnIdle { assertNull(focus.pending) }
    }
    @Test fun aKeyReleaseDoesNotInvalidateTheRequestCreatedByItsPress() {
        board()
        compose.runOnIdle {
            FocusInputEpoch.key(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            focus.request("results")
            FocusInputEpoch.key(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            ready = true
        }
        compose.waitForFocus("result")
    }

    @Test fun anOutgoingDestinationCannotTakeFocusUntilItResumes() {
        board()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            focus.request("results")
            ready = true
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("result").assertIsNotFocused()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForFocus("result")
    }
    @Test fun passiveFocusWhilePausedDoesNotReplaceTheReturnPoint() {
        ready = true; board()
        compose.onNodeWithTag("result").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.onNodeWithTag("query").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForFocus("result")
    }

}