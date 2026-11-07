package mnstudio.apps.mnstreamx.data.caption

import android.system.Os
import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriberOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mnstudio.apps.mnstreamx.domain.caption.CaptionDeadlineException
import mnstudio.apps.mnstreamx.domain.caption.CaptionResourcePolicy
import mnstudio.apps.mnstreamx.domain.model.CaptionStage
import mnstudio.apps.mnstreamx.domain.model.SpeechText
import mnstudio.apps.mnstreamx.domain.repository.SpeechEngine
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
/** Never close the SDK concurrently with native inference. */
internal class MoonshineSpeechEngine(private val folder: File, private val source: String) : SpeechEngine {
    private val cancelled = AtomicBoolean(false)
    private var transcriber: Transcriber? = null
    private var decoded = false
    init { check(configured); load() }
    private companion object {
        val configured: Boolean by lazy { Os.setenv("MOONSHINE_ORT_SINGLE_THREAD", "1", true); true }
    }
    private fun load(): Transcriber {
        transcriber?.let { return it }
        val candidate = Transcriber(listOf(TranscriberOption("max_tokens_per_second", if (source == "ar") "13.0" else "6.5")))
        try {
            candidate.loadFromFiles(folder.absolutePath, JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING)
            return candidate.also { transcriber = it }
        } catch (error: Throwable) { candidate.close(); throw error }
    }
    override val memoryStage get() = when {
        transcriber == null -> CaptionResourcePolicy.MOONSHINE_COLD
        decoded -> CaptionResourcePolicy.LOCAL_WARM
        else -> CaptionResourcePolicy.MOONSHINE_STATE
    }
    override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
        currentCoroutineContext().ensureActive(); check(!cancelled.get())
        require(language == source && samples.size <= 96000)
        val started = System.nanoTime()
        val text = load().transcribeWithoutStreaming(samples, 16000).text().trim().take(2000)
        currentCoroutineContext().ensureActive(); check(!cancelled.get()); decoded = true
        val elapsed = (System.nanoTime() - started) / 1000000
        // The SDK offers no safe in-flight abort. Bound inputs and dispose on the worker.
        if (elapsed > 10000) throw CaptionDeadlineException(CaptionStage.SPEECH, elapsed, 10000)
        return SpeechText(source, text, 0, samples.size * 1000L / 16000)
    }
    override fun cancel() { cancelled.set(true) }
    override fun releaseWorkingMemory() {
        val previous = transcriber; transcriber = null; decoded = false
        try { previous?.close() } finally { NativeWhisper().trimMemory() }
    }
    override fun close() { cancel(); releaseWorkingMemory() }
}