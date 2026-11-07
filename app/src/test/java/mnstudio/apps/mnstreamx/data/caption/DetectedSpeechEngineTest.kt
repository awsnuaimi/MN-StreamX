package mnstudio.apps.mnstreamx.data.caption

import kotlinx.coroutines.test.runTest
import mnstudio.apps.mnstreamx.domain.caption.*
import mnstudio.apps.mnstreamx.domain.model.SpeechText
import mnstudio.apps.mnstreamx.domain.repository.SpeechEngine
import org.junit.Assert.*
import org.junit.Test

class DetectedSpeechEngineTest {
    @Test fun nonSpeechNeverReachesWhisperAndBothResourcesClose() = runTest {
        var calls = 0; var voiceClosed = false; var vadClosed = false
        val engine = DetectedSpeechEngine(object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String): SpeechText { calls++; error("Noise decoded") }
            override fun cancel() = Unit
            override fun close() { voiceClosed = true }
        }, object : CaptionSpeechDetector {
            override suspend fun speech(samples: FloatArray): CaptionSpeechSpan? = null
            override fun cancel() = Unit
            override fun close() { vadClosed = true }
        })
        assertTrue(engine.transcribe(FloatArray(56000), "de", "ar").text.isEmpty())
        assertEquals(0, calls); engine.close(); assertTrue(voiceClosed && vadClosed)
    }
    @Test fun speechTrimmingPreservesTargetAndAudioClockOffsets() = runTest {
        val samples = FloatArray(56000) { it.toFloat() }
        val voice = object : SpeechEngine {
            override suspend fun transcribe(samples: FloatArray, language: String) = error("Target missing")
            override suspend fun transcribe(samples: FloatArray, language: String, target: String): SpeechText {
                assertEquals("de", language); assertEquals("ar", target)
                assertEquals(16000, samples.size); assertEquals(16000f, samples.first())
                return SpeechText("en", "Good morning", 100, 900)
            }
            override fun cancel() = Unit
            override fun close() = Unit
        }
        val gate = object : CaptionSpeechDetector {
            override suspend fun speech(samples: FloatArray) = CaptionSpeechSpan(16000, 32000)
            override fun cancel() = Unit
            override fun close() = Unit
        }
        val result = DetectedSpeechEngine(voice, gate).transcribe(samples, "de", "ar")
        assertEquals(1100L, result.startMillis); assertEquals(1900L, result.endMillis)
    }
    @Test fun edgePaddingKeepsAtLeastOneSecondWithoutExceedingTheAudioWindow() {
        for (span in listOf(CaptionSpeechSpan(0, 1600), CaptionSpeechSpan(55000, 56000))) {
            val result = span.decodeRange(56000)
            assertTrue(result.start <= span.start && result.end >= span.end)
            assertTrue(result.start >= 0 && result.end <= 56000 && result.end - result.start >= 16000)
        }
    }
}