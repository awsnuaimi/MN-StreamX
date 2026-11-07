package mnstudio.apps.mnstreamx.domain.caption

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import mnstudio.apps.mnstreamx.domain.model.*
import mnstudio.apps.mnstreamx.domain.repository.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Routing contract tests; recognizer/translator doubles do not claim model linguistic accuracy. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CaptionLanguageMatrixTest(private val source: CaptionLanguage, private val destination: CaptionLanguage) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0} to {1}") fun languages(): List<Array<Any>> =
            CaptionLanguage.entries.flatMap { from -> CaptionLanguage.entries.filter { it != from }
                .map { to -> arrayOf<Any>(from, to) } }
    }
    @Test fun requestedDirectionReachesBothStagesAndOnlyTheRequestedOutputIsPublished() = runTest {
        val audio = Channel<AudioWindow>(1)
        val input = object : CaptionAudioInput {
            override val positionUs = Long.MIN_VALUE
            override fun open() = CaptionFeed(audio) { }
        }
        val output = mapOf("ar" to "صباح الخير", "en" to "Good morning", "de" to "Guten Morgen",
            "pl" to "Dzień dobry", "hi" to "सुप्रभात").getValue(destination.tag)
        var translated = 0
        val voice = SpeechEngineFactory { object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String) = error("Target-aware call required")
            override suspend fun transcribe(samples: FloatArray, language: String, target: String): SpeechText {
                assertEquals(source.tag, language); assertEquals(destination.tag, target)
                return SpeechText("en", "Good morning", 0, 1000)
            }
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val text = CaptionTranslatorFactory { object : CaptionTranslator {
            override suspend fun prepare(target: String, progress: (Int) -> Unit) { assertEquals(destination.tag, target) }
            override suspend fun translate(text: String): String { translated++; assertEquals("Good morning", text); return output }
            override fun cancel() = Unit
            override fun close() = Unit
        } }
        val pipeline = CaptionPipeline(backgroundScope, StandardTestDispatcher(testScheduler),
            object : CaptionModels { override suspend fun prepare(progress: (Int) -> Unit) = File("synthetic-model") },
            voice, text, CaptionDevice { true })
        pipeline.start(input, CaptionSettings(mode = CaptionMode.GENERATED, source = source.tag, target = destination))
        runCurrent(); audio.send(AudioWindow(FloatArray(80000), 0)); runCurrent()
        assertEquals(output, pipeline.uiState.value.cue?.text)
        assertEquals(if (destination == CaptionLanguage.ENGLISH) 0 else 1, translated)
        pipeline.stop(); runCurrent()
    }
}