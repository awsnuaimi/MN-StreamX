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
class CaptionHighThresholdPipelineTest {
    private class Device(var pressure: Boolean = false) : CaptionDevice {
        override fun canRun(target: String) = assess(target).allowed
        override fun assess(target: String) = CaptionResourcePolicy.memory(target,
            800 * CaptionResourcePolicy.MIB, 900 * CaptionResourcePolicy.MIB, pressure)
    }
    private class Engines {
        var calls = 0
        var opened = false
        var speechClosed = false
        var textClosed = false
        val models = object : CaptionModels {
            override suspend fun prepare(progress: (Int) -> Unit) = File("cached")
        }
        val speech = SpeechEngineFactory {
            opened = true
            object : SpeechEngine {
                override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
                    calls++; return SpeechText("en", "hello", 0, 1000)
                }
                override fun cancel() = Unit
                override fun close() { speechClosed = true }
            }
        }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override suspend fun translate(text: String) = "مرحبا"
            override fun cancel() = Unit
            override fun close() { textClosed = true }
        } }
    }
    private class Input : CaptionAudioInput {
        val windows = Channel<AudioWindow>(1)
        var closed = false
        override val positionUs = 0L
        override fun open() = CaptionFeed(windows) { closed = true }
    }

    @Test fun normalAdmissionProducesACueThenActualPressureClosesBothEngines() = runTest {
        val device = Device(); val e = Engines(); val input = Input()
        val p = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler),
            e.models, e.speech, e.text, device)
        p.start(input, CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        assertTrue(e.opened)
        input.windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertEquals("مرحبا", p.uiState.value.cue?.text)
        assertEquals(1, e.calls)
        device.pressure = true
        input.windows.send(AudioWindow(FloatArray(80000), 5000000)); runCurrent()
        val report = p.uiState.value.progress
        assertEquals(CaptionStatus.MEMORY_PRESSURE, report.status)
        assertEquals(800L, report.availableMb)
        assertEquals(224L, report.requiredMb)
        assertEquals(900L, report.systemThresholdMb)
        assertEquals(CaptionStage.SPEECH, report.stage)
        assertNull(p.uiState.value.cue)
        assertEquals(1, e.calls)
        assertTrue(e.speechClosed && e.textClosed && input.closed)
    }

    @Test fun pressureAtStartupStillPreventsTheFirstNativeAllocation() = runTest {
        val e = Engines(); val p = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler),
            e.models, e.speech, e.text, Device(true))
        p.start(Input(), CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        assertFalse(e.opened); assertTrue(e.textClosed)
        assertEquals(CaptionStatus.MEMORY_PRESSURE, p.uiState.value.progress.status)
        assertEquals(900L, p.uiState.value.progress.systemThresholdMb)
    }
}