package com.yourapp.iptv.ui.screen.vod

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class VodNextCountdownTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val ended = mutableStateOf(false)
    private val enabled = mutableStateOf(false)
    private val active = mutableStateOf(true)
    private val cancelled = mutableStateOf(false)
    private val hasNext = mutableStateOf(true)
    private val mounted = mutableStateOf(true)
    private var calls = 0
    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (mounted.value) Text(vodNextCountdown(ended.value,hasNext.value,enabled.value,active.value,cancelled.value) { calls++ }.toString())
        }
        compose.waitForIdle()
    }
    @Test fun automaticAdvanceRequiresBothCompletionAndAnExplicitChoiceAndFiresOnce() {
        show();compose.mainClock.advanceTimeBy(20000)
        compose.runOnIdle { assertEquals(0,calls);enabled.value = true }
        compose.mainClock.advanceTimeBy(20000)
        compose.runOnIdle { assertEquals(0,calls);ended.value = true }
        compose.mainClock.advanceTimeBy(11000)
        compose.runOnIdle { assertEquals(1,calls) }
        compose.mainClock.advanceTimeBy(20000);compose.runOnIdle { assertEquals(1,calls) }
    }
    @Test fun cancelKeepsTheEndedEpisodeAndDoesNotScheduleAnotherAttempt() {
        ended.value = true;enabled.value = true;show();compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle { cancelled.value = true };compose.mainClock.advanceTimeBy(20000)
        compose.runOnIdle { assertEquals(0,calls) }
    }
    @Test fun lifecycleLossOrDisposalCannotStartTheNextEpisodeInTheBackground() {
        ended.value = true;enabled.value = true;show();compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle { active.value = false;cancelled.value = true }
        compose.mainClock.advanceTimeBy(20000);compose.runOnIdle { assertEquals(0,calls);active.value = true;mounted.value = false }
        compose.mainClock.advanceTimeBy(20000);compose.runOnIdle { assertEquals(0,calls) }
    }
    @Test fun theFinalEpisodeNeverStartsARepetitionOrAnUnrelatedVideo() {
        ended.value = true;enabled.value = true;hasNext.value = false;show()
        compose.mainClock.advanceTimeBy(20000);compose.runOnIdle { assertEquals(0,calls) }
    }
}