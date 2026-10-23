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
class CaptionStagedMemoryTest {
    @Test fun asrWorkingSetIsReleasedBeforeEveryTranslationAndCanReloadForNextWindow() = runTest {
        val events = mutableListOf<String>(); var speechResident = false
        val windows = Channel<AudioWindow>(1)
        val input = object : CaptionAudioInput {
            override val positionUs = 0L
            override fun open() = CaptionFeed(windows) { events += "feed-close" }
        }
        val voice = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
                assertFalse(speechResident); speechResident = true; events += "speech"
                return SpeechText("en", "hello", 0, 1000)
            }
            override fun releaseWorkingMemory() { assertTrue(speechResident); speechResident = false; events += "park" }
            override fun cancel() = Unit
            override fun close() { speechResident = false; events += "speech-close" }
        } }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override suspend fun translate(text: String): String {
                assertFalse("ASR and Marian must never coexist", speechResident); events += "translate"; return "مرحبا"
            }
            override fun cancel() = Unit
            override fun close() { events += "text-close" }
        } }
        val models = object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("cached") }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, voice, text, CaptionDevice { true })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        repeat(2) { windows.send(AudioWindow(FloatArray(80000), it * 5000000L)); runCurrent() }
        assertEquals(listOf("speech", "park", "translate", "speech", "park", "translate"), events)
        assertEquals("مرحبا", pipeline.uiState.value.cue?.text)
        pipeline.stop(); runCurrent(); assertFalse(speechResident)
        assertTrue(events.containsAll(listOf("feed-close", "speech-close", "text-close")))
    }
    @Test fun decoderStageShortageKeepsItsMeasuredNumbersAndClosesEverything() = runTest {
        var voiceClosed = false; var textClosed = false; var parked = false
        val windows = Channel<AudioWindow>(1)
        val input = object : CaptionAudioInput {
            override val positionUs = 0L
            override fun open() = CaptionFeed(windows) { }
        }
        val voice = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String) = SpeechText("en", "hello")
            override fun releaseWorkingMemory() { parked = true }
            override fun cancel() = Unit
            override fun close() { voiceClosed = true }
        } }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override suspend fun translate(text: String): String {
                assertTrue(parked)
                throw CaptionMemoryException(CaptionResourcePolicy.memory(CaptionResourcePolicy.DECODER,
                    250 * CaptionResourcePolicy.MIB, 128 * CaptionResourcePolicy.MIB, false))
            }
            override fun cancel() = Unit
            override fun close() { textClosed = true }
        } }
        val models = object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("cached") }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, voice, text, CaptionDevice { true })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertEquals(CaptionStatus.LOW_MEMORY, pipeline.uiState.value.progress.status)
        assertEquals(250L, pipeline.uiState.value.progress.availableMb)
        assertEquals(288L, pipeline.uiState.value.progress.requiredMb)
        assertEquals(128L, pipeline.uiState.value.progress.systemThresholdMb)
        assertEquals(CaptionStage.TRANSLATION, pipeline.uiState.value.progress.stage)
        assertTrue(voiceClosed); assertTrue(textClosed); assertNull(pipeline.uiState.value.cue)
    }
}