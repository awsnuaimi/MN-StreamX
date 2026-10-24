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
                    speechCalls++; time += 100
                    return SpeechText("en", "Good morning", 0, samples.size * 1000L / 16000)
                }
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
    @Test fun isolatedTranslatorTimeoutDoesNotTerminateTheLiveFeed() = runTest {
        val session = Session(this); session.failures += true; session.start(); session.audio(5000)
        assertEquals(CaptionStatus.ADAPTING, session.pipeline.uiState.value.progress.status)
        assertTrue(session.pipeline.uiState.value.cue?.text?.contains("untranslated") == true)
        assertEquals(1, session.textCalls)
        assertFalse(session.feedClosed)
        session.audio(5000, 5000000)
        session.audio(5000, 10000000)
        assertEquals(1, session.textCalls)
        session.audio(5000, 15000000)
        assertEquals("translated:Good morning", session.pipeline.uiState.value.cue?.text)
        assertEquals(CaptionStatus.LISTENING, session.pipeline.uiState.value.progress.status)
        session.stop()
        assertEquals(1, session.speechClosed); assertEquals(1, session.textClosed)
    }
    @Test fun sustainedTranslatorOverloadOpensBoundedCircuitWithoutStoppingPlayer() = runTest {
        val session = Session(this)
        repeat(3) { session.failures += true }
        session.start()
        repeat(9) { session.audio(5000, it * 5000000L) }
        assertEquals(3, session.textCalls)
        assertEquals(CaptionStatus.ADAPTING, session.pipeline.uiState.value.progress.status)
        assertFalse(session.feedClosed)
        assertEquals(9, session.speechCalls)
        session.stop()
        assertTrue(session.feedClosed)
        assertEquals(1, session.speechClosed)
    }
    @Test fun failedTranslationStillShowsIdentifiedSourceTextAndCanRecover() = runTest {
        val session = Session(this)
        session.failures += true
        session.start(); session.audio(5000)
        assertEquals("[EN · untranslated] Good morning", session.pipeline.uiState.value.cue?.text)
        repeat(2) { session.audio(5000, (it + 1) * 5000000L) }
        assertEquals(1, session.textCalls)
        session.audio(5000, 15000000)
        assertEquals(2, session.textCalls)
        assertEquals("translated:Good morning", session.pipeline.uiState.value.cue?.text)
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