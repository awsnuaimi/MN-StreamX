package mnstudio.apps.mnstreamx.ui.screen.player

import android.app.Application
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import mnstudio.apps.mnstreamx.R
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryPhase
import mnstudio.apps.mnstreamx.domain.model.LiveRecoveryState
import mnstudio.apps.mnstreamx.ui.theme.IPTVAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h720dp-mdpi")
class PlayerRecoveryOverlayTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun initialLoadingIndicatorIsImmediate() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IPTVAppTheme {
                LiveBufferingIndicator(showImmediately = true, modifier = Modifier.testTag("live-buffering"))
            }
        }
        compose.onNodeWithTag("live-buffering").assertIsDisplayed()
    }

    @Test fun onlySustainedBufferingShowsTheIndicatorAfterPlaybackHasStarted() {
        val buffering = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IPTVAppTheme {
                Box(Modifier.fillMaxSize()) {
                    if (buffering.value) {
                        LiveBufferingIndicator(showImmediately = false, modifier = Modifier.testTag("live-buffering"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("live-buffering").assertDoesNotExist()
        compose.runOnIdle { buffering.value = false }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("live-buffering").assertDoesNotExist()

        compose.runOnIdle { buffering.value = true }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("live-buffering").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("live-buffering").assertIsDisplayed()
        compose.runOnIdle { buffering.value = false }
        compose.onNodeWithTag("live-buffering").assertDoesNotExist()
    }

    @Test fun reschedulingUpdatesTheCountdownAndConnectingReplacesIt() {
        val recovery = mutableStateOf(
            LiveRecoveryState(LiveRecoveryPhase.SCHEDULED, 7, SystemClock.elapsedRealtime() + 30_000L)
        )
        compose.setContent {
            IPTVAppTheme {
                PlayerErrorOverlay(
                    error = null,
                    canRetry = true,
                    hasChannels = true,
                    focus = remember { FocusRequester() },
                    onRetry = {},
                    onChannels = {},
                    onBack = {},
                    recovery = recovery.value
                )
            }
        }
        val firstCountdown = compose.activity.getString(R.string.live_recovery_countdown, 7, "00:30")
        compose.onNodeWithText(firstCountdown).assertIsDisplayed()

        compose.runOnIdle {
            recovery.value = recovery.value.copy(retryAtElapsedRealtimeMs = SystemClock.elapsedRealtime() + 8_000L)
        }
        val updatedCountdown = compose.activity.getString(R.string.live_recovery_countdown, 7, "00:08")
        compose.onNodeWithText(updatedCountdown).assertIsDisplayed()
        compose.onNodeWithText(firstCountdown).assertDoesNotExist()

        compose.runOnIdle {
            recovery.value = LiveRecoveryState(LiveRecoveryPhase.CONNECTING, 7)
        }
        val connecting = compose.activity.getString(R.string.live_recovery_connecting, 7)
        compose.onNodeWithText(connecting).assertIsDisplayed()
        compose.onNodeWithText(updatedCountdown).assertDoesNotExist()
    }
}