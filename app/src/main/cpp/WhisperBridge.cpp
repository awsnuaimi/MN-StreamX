package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionPipelineRecoveryTest {
    private class Session(val scope: TestScope) {
        val frames = Channel<AudioWindow>(1)
        val limits = mutableListOf<Int>()
        val failures = ArrayDeque<Boolean>()
        val speechFailures = ArrayDeque<Boolean>()
        var speechRecoveries = 0
        var time = 0L; var speechCalls = 0; var textCalls = 0
        var speechClosed = 0; var textClosed = 0; var cancelled = 0; var feedClosed = false
        val input = object : CaptionAudioInput {
            override val positionUs = Long.MIN_VALUE
            override fun limitWindow(millis: Int) { limits += millis }
            override fun open() = CaptionFeed(frames) { feedClosed = true }
        }
        val pipeline = CaptionPipeline(scope.backgroundScope, StandardTestDispatcher(scope.testScheduler),
            object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("verified-model") },
            SpeechEngineFactory { object : SpeechEngine {
                override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
                    speechCalls++
                    if (speechFailures.removeFirstOrNull() == true) {
                        time += 10025
                        throw CaptionDeadlineException(CaptionStage.SPEECH, 10025, 10000)
                    }
                    time += 100
                    return SpeechText("en", "Good morning", 0, samples.size * 1000L / 16000)
                }
                override fun recoverAfterDeadline() { speechRecoveries++ }
                override fun cancel() { cancelled++ }
                override fun close() { speechClosed++ }
            } }, CaptionTranslatorFactory { object : CaptionTranslator {
                override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
                override suspend fun translate(text: String): String {
                    textCalls++
                    if (failures.removeFirstOrNull() == true) {
                        time += 8036
                        throw CaptionDeadlineException(CaptionStage.TRANSLATION, 8036, 8000)
                    }
                    time += 100
                    return "translated:$text"
                }
                override fun cancel() { cancelled++ }
                override fun close() { textClosed++ }
            } }, CaptionDevice { true }, now = { time })
        fun start() {
            pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED))
            scope.runCurrent()
        }
        suspend fun audio(millis: Int, startUs: Long = 0) {
            frames.send(AudioWindow(FloatArray(millis * 16) { 0.2f }, startUs))
            scope.runCurrent()
        }
        fun stop() { pipeline.stop(); scope.runCurrent() }
    }
    @Test fun anIsolatedDeadlineShortensAudioAndTheNextWindowPublishesNormally() = runTest {
        val session = Session(this); session.failures += true; session.start(); session.audio(5000)
        assertEquals(CaptionStatus.ADAPTING, session.pipeline.uiState.value.progress.status)
        assertEquals(listOf(5000, 3500), session.limits)
        assertEquals(0, session.cancelled); assertEquals(0, session.textClosed)
        assertFalse(session.feedClosed)
        session.audio(3500, 5000000)
        assertEquals("translated:Good morning", session.pipeline.uiState.value.cue?.text)
        assertEquals(CaptionStatus.LISTENING, session.pipeline.uiState.value.progress.status)
        session.stop()
        assertEquals(1, session.speechClosed); assertEquals(1, session.textClosed)
    }
    @Test fun aQueuedOldLongWindowIsDiscardedInsteadOfRetryingTheHeavyWork() = runTest {
        val session = Session(this); session.failures += true; session.start(); session.audio(5000)
        session.audio(5000, 5000000)
        assertEquals(1, session.speechCalls); assertEquals(1, session.textCalls)
        session.audio(3500, 10000000)
        assertEquals(2, session.textCalls); assertNotNull(session.pipeline.uiState.value.cue)
        session.stop()
    }
    @Test fun threeConsecutiveTranslationDeadlinesStillStopAndReleaseResources() = runTest {
        val session = Session(this); repeat(3) { session.failures += true }; session.start()
        session.audio(5000); session.audio(3500, 5000000); session.audio(2500, 8500000)
        val progress = session.pipeline.uiState.value.progress
        assertEquals(CaptionStatus.SLOW_DEVICE, progress.status)
        assertEquals(CaptionStage.TRANSLATION, progress.stage)
        assertEquals(8000L, progress.budgetMillis); assertEquals(8036L, progress.elapsedMillis)
        assertEquals(listOf(5000, 3500, 2500), session.limits)
        assertTrue(session.feedClosed)
        assertEquals(1, session.speechClosed); assertEquals(1, session.textClosed)
    }
    @Test fun successfulWindowsResetTheFailureStreakButKeepTheLighterWindowSize() = runTest {
        val session = Session(this)
        repeat(4) { session.failures += true; session.failures += false }
        session.start()
        var pts = 0L
        repeat(4) {
            session.audio(session.limits.last(), pts); pts += 5000000
            session.audio(session.limits.last(), pts); pts += 5000000
            assertNotNull(session.pipeline.uiState.value.cue)
            assertFalse(session.feedClosed)
        }
        assertEquals(8, session.textCalls); assertEquals(2500, session.limits.last())
        session.stop()
    }
    @Test fun speechDeadlineResetsDecoderAndContinuesWithShorterFreshWindows() = runTest {
        val session = Session(this)
        session.speechFailures += true; session.start(); session.audio(5000)
        assertEquals(CaptionStatus.ADAPTING, session.pipeline.uiState.value.progress.status)
        assertEquals(listOf(5000, 3500), session.limits)
        assertEquals(1, session.speechRecoveries)
        assertFalse(session.feedClosed)
        session.audio(5000, 5000000)
        assertEquals(1, session.speechCalls)
        session.audio(3500, 10000000)
        assertEquals("translated:Good morning", session.pipeline.uiState.value.cue?.text)
        assertEquals(2, session.speechCalls)
        session.stop()
        assertEquals(1, session.speechClosed); assertEquals(1, session.textClosed)
    }
    @Test fun threeSpeechDeadlinesStopWithoutInfiniteRetryOrModelReload() = runTest {
        val session = Session(this); repeat(3) { session.speechFailures += true }; session.start()
        session.audio(5000)
        session.audio(3500, 5000000)
        session.audio(2500, 8500000)
        val progress = session.pipeline.uiState.value.progress
        assertEquals(CaptionStatus.SLOW_DEVICE, progress.status)
        assertEquals(CaptionStage.SPEECH, progress.stage)
        assertEquals(10000L, progress.budgetMillis)
        assertEquals(10025L, progress.elapsedMillis)
        assertEquals(2, session.speechRecoveries)
        assertEquals(listOf(5000, 3500, 2500), session.limits)
        assertEquals(1, session.speechClosed); assertEquals(1, session.textClosed)
        assertTrue(session.feedClosed)
    }
    @Test fun goodSpeechAfterTimeoutResetsFailureCounterWithoutExpandingTheWindow() = runTest {
        val session = Session(this); session.speechFailures.addAll(listOf(true, false, true, false, true, false))
        session.start(); var startUs = 0L
        repeat(3) {
            session.audio(session.limits.last(), startUs); startUs += 5000000
            session.audio(session.limits.last(), startUs); startUs += 5000000
            assertNotNull(session.pipeline.uiState.value.cue)
            assertFalse(session.feedClosed)
        }
        assertEquals(3, session.speechRecoveries)
        assertEquals(2500, session.limits.last())
        session.stop()
    }
    @Test fun stoppingDuringAdaptationCannotResumeInferenceOnLaterAudio() = runTest {
        val session = Session(this); session.failures += true; session.start(); session.audio(5000)
        session.stop(); session.audio(3500, 5000000)
        assertEquals(1, session.textCalls); assertNull(session.pipeline.uiState.value.cue)
        assertEquals(CaptionStatus.OFF, session.pipeline.uiState.value.progress.status)
        assertTrue(session.feedClosed)
    }
}