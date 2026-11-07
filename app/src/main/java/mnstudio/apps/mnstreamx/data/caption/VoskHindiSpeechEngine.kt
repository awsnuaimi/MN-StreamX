package mnstudio.apps.mnstreamx.data.caption

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mnstudio.apps.mnstreamx.domain.caption.*
import mnstudio.apps.mnstreamx.domain.model.CaptionStage
import mnstudio.apps.mnstreamx.domain.model.SpeechText
import mnstudio.apps.mnstreamx.domain.repository.SpeechEngine
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
internal class VoskHindiSpeechEngine(private val folder: File) : SpeechEngine {
    private val cancelled = AtomicBoolean(false)
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var decoded = false
    init { load() }
    private fun load(): Recognizer {
        recognizer?.let { return it }
        val candidate = model ?: Model(folder.absolutePath).also { model = it }
        try { return Recognizer(candidate, 16000f).also { recognizer = it; it.setWords(true) } }
        catch (error: Throwable) { releaseWorkingMemory(); throw error }
    }
    override val memoryStage get() = when {
        model == null -> CaptionResourcePolicy.VOSK_COLD
        decoded -> CaptionResourcePolicy.LOCAL_WARM
        else -> CaptionResourcePolicy.VOSK_STATE
    }
    override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
        currentCoroutineContext().ensureActive(); check(!cancelled.get())
        require(language == "hi" && samples.size <= 96000)
        val started = System.nanoTime(); val voice = load(); voice.reset()
        val parts = mutableListOf<String>()
        fun append(json: String) { JSONObject(json).optString("text").takeIf { it.isNotBlank() }?.let(parts::add) }
        var offset = 0
        while (offset < samples.size) {
            currentCoroutineContext().ensureActive(); check(!cancelled.get())
            val elapsed = (System.nanoTime() - started) / 1000000
            if (elapsed > 10000) throw CaptionDeadlineException(CaptionStage.SPEECH, elapsed, 10000)
            val count = minOf(4000, samples.size - offset)
            val chunk = CaptionPcm16.shorts(samples, offset, count)
            if (voice.acceptWaveForm(chunk, chunk.size)) append(voice.result)
            offset += count
        }
        append(voice.finalResult)
        currentCoroutineContext().ensureActive(); check(!cancelled.get()); decoded = true
        val elapsed = (System.nanoTime() - started) / 1000000
        if (elapsed > 10000) throw CaptionDeadlineException(CaptionStage.SPEECH, elapsed, 10000)
        return SpeechText("hi", parts.joinToString(" ").trim().take(2000), 0, samples.size * 1000L / 16000)
    }
    override fun cancel() { cancelled.set(true) }
    override fun releaseWorkingMemory() {
        val previous = recognizer; recognizer = null; decoded = false
        try { previous?.close() } finally {
            val previousModel = model; model = null
            try { previousModel?.close() } finally { NativeWhisper().trimMemory() }
        }
    }
    override fun close() { cancel(); releaseWorkingMemory() }
}