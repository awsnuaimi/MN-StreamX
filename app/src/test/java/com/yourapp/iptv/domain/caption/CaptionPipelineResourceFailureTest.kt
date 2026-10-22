package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionPipelineResourceFailureTest {
    private val enabled = CaptionSettings(mode = CaptionMode.GENERATED)
    private val input = object : CaptionAudioInput {
        override val positionUs = 0L
        override fun open(): CaptionFeed = error("No audio feed should open during a resource failure")
    }
    private val speech = SpeechEngineFactory { error("No speech engine should open") }
    @Test fun storageFailureIsReportedWithDiskBytesRatherThanAsLowRam() = runTest {
        val models = object : CaptionModels {
            override suspend fun prepare(progress: (Int) -> Unit): File = throw CaptionStorageException(2 * 1048576L, 48 * 1048576L)
        }
        val text = CaptionTranslatorFactory { error("No translator should be created") }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, speech, text, CaptionDevice { true })
        pipeline.start(input, enabled); runCurrent()
        val result = pipeline.uiState.value.progress
        assertEquals(CaptionStatus.LOW_STORAGE, result.status)
        assertEquals(2L, result.availableMb); assertEquals(48L, result.requiredMb)
    }
    @Test fun translationDownloadFailureClosesTheDownloaderAndNeverPreparesNativeSessions() = runTest {
        var closed = false
        val models = object : CaptionModels {
            override suspend fun prepare(progress: (Int) -> Unit) = File("cached-speech")
        }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun download(target: String, progress: (Int) -> Unit): Unit =
                throw CaptionStorageException(20 * 1048576L, 120 * 1048576L)
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = error("No native sessions")
            override suspend fun translate(text: String) = error("No inference")
            override fun cancel() = Unit
            override fun close() { closed = true }
        } }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, speech, text, CaptionDevice { true })
        pipeline.start(input, enabled); runCurrent()
        assertEquals(CaptionStatus.LOW_STORAGE, pipeline.uiState.value.progress.status); assertTrue(closed)
    }
    @Test fun remainingRamIsCheckedAfterPreparingTokenizerAndBeforeOpeningSpeech() = runTest {
        var prepared = false; var closed = false
        val models = object : CaptionModels {
            override suspend fun prepare(progress: (Int) -> Unit) = File("cached-speech")
        }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) { prepared = true }
            override suspend fun translate(text: String) = text
            override fun cancel() = Unit
            override fun close() { closed = true }
        } }
        val device = object : CaptionDevice {
            override fun canRun(target: String) = true
            override fun assess(target: String) = CaptionResourcePolicy.memory(target,
                (if (prepared) 200L else 800L) * 1048576, 64 * 1048576L, false)
        }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, speech, text, device)
        pipeline.start(input, enabled); runCurrent()
        assertTrue(prepared); assertTrue(closed)
        val result = pipeline.uiState.value.progress
        assertEquals(CaptionStatus.LOW_MEMORY, result.status); assertEquals(200L, result.availableMb); assertEquals(224L, result.requiredMb)
    }
}