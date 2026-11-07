package mnstudio.apps.mnstreamx.data.caption

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mnstudio.apps.mnstreamx.domain.caption.CaptionSpeechDetector
import mnstudio.apps.mnstreamx.domain.model.SpeechText
import mnstudio.apps.mnstreamx.domain.repository.SpeechEngine

/** Run VAD on the caption worker before Whisper, retaining original media-clock offsets. */
internal class DetectedSpeechEngine(private val voice: SpeechEngine,
    private val detector: CaptionSpeechDetector) : SpeechEngine {
    override val memoryStage get() = voice.memoryStage
    override suspend fun transcribe(samples: FloatArray, language: String) = transcribe(samples, language, false)
    override suspend fun transcribe(samples: FloatArray, language: String, translateToEnglish: Boolean): SpeechText =
        filtered(samples, language) { voice.transcribe(it, language, translateToEnglish) }
    override suspend fun transcribe(samples: FloatArray, language: String, target: String): SpeechText =
        filtered(samples, language) { voice.transcribe(it, language, target) }

    private suspend fun filtered(samples: FloatArray, language: String,
        decode: suspend (FloatArray) -> SpeechText): SpeechText {
        val span = detector.speech(samples) ?: return SpeechText(language, "")
        currentCoroutineContext().ensureActive()
        val selected = if (span.start == 0 && span.end == samples.size) samples else samples.copyOfRange(span.start, span.end)
        val text = decode(selected)
        val offset = span.start * 1000L / 16000
        return text.copy(startMillis = text.startMillis + offset, endMillis = text.endMillis + offset)
    }
    override fun releaseWorkingMemory() = voice.releaseWorkingMemory()
    override fun recoverAfterDeadline() = voice.recoverAfterDeadline()
    override fun cancel() { try { detector.cancel() } finally { voice.cancel() } }
    override fun close() { try { voice.close() } finally { detector.close() } }
}