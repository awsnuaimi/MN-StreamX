package mnstudio.apps.mnstreamx.domain.caption

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionLanguageDetectionTest {
    private class Session(scope: TestScope, val detected: String = "pl") {
        val windows = Channel<AudioWindow>(1)
        val languages = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val closed = mutableListOf<String>()
        val routes = mutableListOf<Pair<String, String>>()
        val translated = mutableListOf<String>()
        private val input = object : CaptionAudioInput {
            override val positionUs = Long.MIN_VALUE
            override fun open() = CaptionFeed(windows) { }
        }
        private val models = object : CaptionModels {
            override suspend fun prepare(progress: (Int) -> Unit) = error("Source required")
            override suspend fun prepare(source: String, progress: (Int) -> Unit) = File(source)
        }
        val pipeline = CaptionPipeline(scope.backgroundScope, StandardTestDispatcher(scope.testScheduler), models,
            SpeechEngineFactory { file ->
                opened += file.name
                object : SpeechEngine {
                    override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
                        languages += language
                        return if (language == "auto") SpeechText(detected, "", languageOnly = true)
                            else SpeechText(language, "Dzień dobry. Pociąg odjeżdża.", 0, 2000)
                    }
                    override fun cancel() = Unit
                    override fun close() { closed += file.name }
                }
            }, CaptionTranslatorFactory { object : CaptionTranslator {
                override suspend fun prepare(target: String, progress: (Int) -> Unit) = error("Route required")
                override suspend fun prepareRoute(source: String, target: String, progress: (Int) -> Unit) { routes += source to target }
                override suspend fun translate(text: String): String { translated += text; return "صباح الخير. يغادر القطار." }
                override fun cancel() = Unit
                override fun close() = Unit
            } }, CaptionDevice { true })
        fun start(source: String = "auto") = pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED, source = source))
    }
    @Test fun polishDetectionNeedsNoFakeTranscriptAndPreparesTheArabicRouteBeforeTheNextWindow() = runTest {
        val session = Session(this); session.start(); runCurrent()
        session.windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertNull(session.pipeline.uiState.value.cue)
        assertTrue(session.translated.isEmpty())
        assertEquals(listOf("auto"), session.languages)
        assertEquals(listOf("auto"), session.opened)
        assertEquals(listOf("auto" to "ar", "pl" to "ar"), session.routes)
        session.windows.send(AudioWindow(FloatArray(80000), 5000000)); runCurrent()
        assertEquals(listOf("auto", "pl"), session.languages)
        assertEquals(listOf("Dzień dobry. Pociąg odjeżdża."), session.translated)
        assertEquals("صباح الخير. يغادر القطار.", session.pipeline.uiState.value.cue?.text)
        session.pipeline.stop(); runCurrent(); assertEquals(listOf("auto"), session.closed)
    }
    @Test fun selectingPolishExplicitlySkipsDetectionAndPublishesArabic() = runTest {
        val session = Session(this); session.start("pl"); runCurrent()
        session.windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertEquals(listOf("pl"), session.languages)
        assertEquals(listOf("pl" to "ar"), session.routes)
        assertEquals("صباح الخير. يغادر القطار.", session.pipeline.uiState.value.cue?.text)
        session.pipeline.stop(); runCurrent()
    }
    @Test fun languageOnlyResultSwitchesToTheDedicatedRecognizerAndNeverReachesTheTranslator() = runTest {
        val session = Session(this, "de"); session.start(); runCurrent()
        session.windows.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertEquals(listOf("auto", "de"), session.opened)
        assertEquals(listOf("auto"), session.closed)
        assertTrue(session.translated.isEmpty()); assertNull(session.pipeline.uiState.value.cue)
        session.pipeline.stop(); runCurrent(); assertEquals(listOf("auto", "de"), session.closed)
    }
}