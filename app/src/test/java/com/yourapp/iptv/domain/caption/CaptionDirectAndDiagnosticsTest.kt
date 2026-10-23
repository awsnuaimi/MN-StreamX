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
class CaptionDirectAndDiagnosticsTest {
    @Test fun identicalLanguagesUseNativeTranscriptionWithoutTextModelDownloadOrTranslation() = runTest {
        var textTarget = ""; var nativeTranslate: Boolean? = null
        val audio = Channel<AudioWindow>(1)
        val input = object : CaptionAudioInput {
            override val positionUs = 0L
            override fun open() = CaptionFeed(audio) { }
        }
        val voice = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String) = error("New API expected")
            override suspend fun transcribe(samples: FloatArray, language: String, translateToEnglish: Boolean): SpeechText {
                nativeTranslate = translateToEnglish; return SpeechText("ar", "صباح الخير")
            }
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun download(target: String, progress: (Int) -> Unit) { textTarget = target }
            override suspend fun prepare(target: String, progress: (Int) -> Unit) { assertEquals("en", target) }
            override suspend fun translate(text: String) = error("No translation expected")
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val models = object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("cached") }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, voice, text,
            CaptionDevice { true }, now = { testScheduler.currentTime })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED, source = "ar", target = CaptionLanguage.ARABIC))
        runCurrent(); audio.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertEquals("en", textTarget); assertEquals(false, nativeTranslate)
        assertEquals("صباح الخير", pipeline.uiState.value.cue?.text)
        assertEquals(0L, pipeline.uiState.value.timing?.translationMillis)
        pipeline.stop(); runCurrent()
    }
    @Test fun aSpeechDeadlineAdaptsBeforeReportingSlowDevice() = runTest {
        val audio = Channel<AudioWindow>(1)
        val input = object : CaptionAudioInput {
            override val positionUs = 0L
            override fun open() = CaptionFeed(audio) { }
        }
        val voice = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String): SpeechText =
                throw CaptionDeadlineException(CaptionStage.SPEECH, 10025, 10000)
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override suspend fun translate(text: String) = error("Speech did not complete")
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val models = object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("cached") }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, voice, text, CaptionDevice { true })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED)); runCurrent()
        audio.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertEquals(CaptionStatus.ADAPTING, pipeline.uiState.value.progress.status)
        assertNull(pipeline.uiState.value.cue)
        pipeline.stop(); runCurrent()
    }
}