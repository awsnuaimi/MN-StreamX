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
class CaptionMemoryHandoffTest {
    @Test fun retainedTextDecoderIsReleasedBeforeEverySubsequentSpeechWindowEvenWithPlentyOfReportedFreeRam() = runTest {
        var textResident = false
        var speechResident = false
        var translated = 0
        val windows = Channel<AudioWindow>(1)
        val events = mutableListOf<String>()
        val input = object : CaptionAudioInput {
            override val positionUs = 0L
            override fun open() = CaptionFeed(windows) { }
        }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override fun releaseWorkingMemory() { textResident = false; events += "release-text" }
            override suspend fun translate(text: String): String {
                assertFalse(speechResident); textResident = true; translated++; events += "translate"
                return "translated"
            }
            override fun cancel() = Unit
            override fun close() { textResident = false }
        } }
        val speech = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
                assertFalse("Retained text decoder overlaps restored speech state", textResident)
                speechResident = true; events += "speech"
                return SpeechText("en", "hello")
            }
            override fun releaseWorkingMemory() { speechResident = false; events += "release-speech" }
            override fun cancel() = Unit
            override fun close() { speechResident = false }
        } }
        val device = object : CaptionDevice {
            override fun canRun(target: String): Boolean {
                assertFalse("RAM assessment must happen after text release", textResident)
                return true
            }
        }
        val models = object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("cached") }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, speech, text, device)
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        repeat(6) { windows.send(AudioWindow(FloatArray(80000), it * 5000000L)); runCurrent() }
        assertEquals(6, translated)
        assertEquals(List(6) { listOf("release-text", "speech", "release-speech", "translate") }.flatten(), events)
        pipeline.stop(); runCurrent()
        assertFalse(speechResident); assertFalse(textResident)
    }

    @Test fun handoffFailureClosesBothEnginesWithoutPublishingACue() = runTest {
        var voiceClosed = false; var textClosed = false
        val windows = Channel<AudioWindow>(1)
        val input = object : CaptionAudioInput {
            override val positionUs = 0L
            override fun open() = CaptionFeed(windows) { }
        }
        val models = object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("cached") }
        val speech = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String) = error("Handoff failed")
            override fun cancel() = Unit
            override fun close() { voiceClosed = true }
        } }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override fun releaseWorkingMemory() = error("Cannot release working memory")
            override suspend fun translate(text: String) = error("No translation expected")
            override fun cancel() = Unit
            override fun close() { textClosed = true }
        } }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, speech, text, CaptionDevice { true })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertTrue(voiceClosed); assertTrue(textClosed)
        assertEquals(CaptionStatus.FAILED, pipeline.uiState.value.progress.status)
        assertNull(pipeline.uiState.value.cue)
    }
}