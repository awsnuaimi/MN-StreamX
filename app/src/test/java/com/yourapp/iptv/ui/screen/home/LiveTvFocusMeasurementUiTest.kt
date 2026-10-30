package com.yourapp.iptv.ui.screen.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.performance.*
import com.yourapp.iptv.ui.presentation.*
import com.yourapp.iptv.ui.theme.IPTVAppTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalComposeUiApi::class)
class LiveTvFocusMeasurementUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var now = 0L
    private val recorder = PerformanceRecorder { now }
    private val navigation = LiveTvListNavigation(PerformanceSpans(recorder))
    private val ready = mutableStateOf(false)
    private val rows = (0 until 200).map { n ->
        val channel = Channel("$n", "Station $n HD", streamUrl = "synthetic:$n")
        LiveChannelRow(channel.id, channel.name, listOf(channel), number = n + 1)
    }
    private fun board() {
        compose.setContent {
            IPTVAppTheme {
                val input = LocalInputModeManager.current
                SideEffect { input.requestInputMode(InputMode.Keyboard) }
                val category = remember { FocusRequester() }; val watch = remember { FocusRequester() }
                Surface(Modifier.fillMaxSize()) {
                    LiveTvChannelList(rows, null, "0", null, HomeLabels(HomeLanguage.ENGLISH),
                        liveTvColors(HomeAppearance()), { ChannelProgramInfo() }, { null }, {}, {}, {}, {},
                        { _, _ -> }, {}, category, watch, false, Modifier.fillMaxSize(), showLogos = false,
                        navigation = navigation, ready = ready.value)
                }
            }
        }
        compose.waitForIdle()
    }
    @Test fun anOffscreenRequestIsNotCompletedUntilTheRealRowHasFocus() {
        navigation.request("150", instant = true); board()
        compose.runOnIdle { assertTrue(recorder.snapshot().isEmpty()); now = 12_000_000; ready.value = true }
        compose.waitForIdle()
        compose.waitUntil(10_000) { navigation.requests.value == null }
        compose.onNodeWithTag("live-channel-150").assertIsDisplayed().assertIsFocused()
        compose.runOnIdle {
            val metric = recorder.snapshot().single()
            assertEquals(1, metric.complete); assertEquals(12L, metric.p95Ms)
        }
    }
    @Test fun aSupersededPendingTargetNeverProducesASuccessfulLatencySample() {
        navigation.request("10", instant = true); board()
        compose.runOnIdle { now = 3_000_000; navigation.request("170", instant = true); now = 8_000_000; ready.value = true }
        compose.waitForIdle()
        compose.waitUntil(10_000) { navigation.requests.value == null }
        compose.onNodeWithTag("live-channel-170").assertIsDisplayed().assertIsFocused()
        compose.runOnIdle {
            val metric = recorder.snapshot().single()
            assertEquals(1, metric.cancelled); assertEquals(1, metric.complete); assertEquals(5L, metric.p95Ms)
        }
    }
}