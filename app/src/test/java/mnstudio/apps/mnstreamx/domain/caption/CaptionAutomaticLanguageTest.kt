package mnstudio.apps.mnstreamx.domain.caption

import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionAutomaticLanguageTest {
    @Test fun autoSwitchClosesTheInitialRecognizerAndDiscardsItsDetectionWindow() = runTest {
        val windows = Channel<AudioWindow>(1)
        val input = object : CaptionAudioInput {
            override val positionUs = Long.MIN_VALUE
            override fun open() = CaptionFeed(windows) { }
        }
        val events = mutableListOf<String>(); var resident = 0
        val speech = SpeechEngineFactory { model ->
            assertEquals(0, resident); resident++; val expected = model.name; events += "open-$expected"
            object : SpeechEngine {
                override suspend fun transcribe(samples: FloatArray, language: String) = error("Boolean API required")
                override suspend fun transcribe(samples: FloatArray, language: String, translateToEnglish: Boolean): SpeechText {
                    assertFalse(translateToEnglish); assertEquals(expected, language); events += "decode-$language"
                    return SpeechText("de", if (language == "auto") "Old detection text" else "Der Zug kommt an.")
                }
                override fun cancel() = Unit
                override fun close() { resident--; events += "close-$expected" }
            }
        }
        val translators = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) = Unit
            override suspend fun translate(text: String) = error("Identity route must stay unchanged")
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val models = object : CaptionModels {
            override suspend fun prepare(progress: (Int) -> Unit) = error("Source required")
            override suspend fun prepare(source: String, progress: (Int) -> Unit) = File(source)
        }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler), models, speech, translators, CaptionDevice { true })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED, target = CaptionLanguage.GERMAN))
        runCurrent(); windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertNull(pipeline.uiState.value.cue)
        assertEquals(listOf("open-auto", "decode-auto", "close-auto", "open-de"), events)
        windows.send(AudioWindow(FloatArray(80000), 5000000)); runCurrent()
        assertEquals("Der Zug kommt an.", pipeline.uiState.value.cue?.text)
        pipeline.stop(); runCurrent(); assertEquals(0, resident)
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