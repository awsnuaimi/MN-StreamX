package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionAutomaticLanguageTest {
    @Test fun automaticSameLanguageTranscriptionBypassesTextTranslation() = runTest {
        val windows = Channel<AudioWindow>(1)
        val input = object : CaptionAudioInput {
            override val positionUs = 0L
            override fun open() = CaptionFeed(windows) { }
        }
        var receivedTarget = ""
        val speech = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String) = error("Target-aware method expected")
            override suspend fun transcribe(samples: FloatArray, language: String, target: String): SpeechText {
                assertEquals("auto", language); receivedTarget = target
                return SpeechText("de", "Der Zug kommt um acht Uhr an.")
            }
            override fun cancel() = Unit
            override fun close() = Unit
            override fun releaseWorkingMemory() = error("Direct transcription must retain its warm speech state")
        } }
        val translators = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override suspend fun translate(text: String) = error("Same-language text must stay unchanged")
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val models = object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("model") }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, speech, translators, CaptionDevice { true })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED, target = CaptionLanguage.GERMAN))
        runCurrent(); windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertEquals("de", receivedTarget)
        assertEquals("Der Zug kommt um acht Uhr an.", pipeline.uiState.value.cue?.text)
        pipeline.stop(); runCurrent()
    }

    @Test fun changingChannelsWaitsForOldNativeCleanupBeforeAllocatingTheNextEngine() = runTest {
        val gate = CompletableDeferred<Unit>()
        var opened = 0; var resident = 0; var maximum = 0
        val inputs = List(2) {
            object : CaptionAudioInput {
                val windows = Channel<AudioWindow>(1)
                override val positionUs = 0L
                override fun open() = CaptionFeed(windows) { }
            }
        }
        val speech = SpeechEngineFactory {
            opened++; resident++; maximum = maxOf(maximum, resident)
            object : SpeechEngine {
                override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
                    withContext(NonCancellable) { gate.await() }
                    return SpeechText("en", "Hello")
                }
                override fun cancel() = Unit
                override fun close() { resident-- }
            }
        }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override suspend fun translate(text: String) = text
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val models = object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("model") }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, speech, text, CaptionDevice { true })
        pipeline.start(inputs[0], CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        inputs[0].windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        pipeline.start(inputs[1], CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        assertEquals(1, opened)
        gate.complete(Unit); runCurrent()
        assertEquals(2, opened); assertEquals(1, maximum)
        pipeline.stop(); runCurrent(); assertEquals(0, resident)
    }
}