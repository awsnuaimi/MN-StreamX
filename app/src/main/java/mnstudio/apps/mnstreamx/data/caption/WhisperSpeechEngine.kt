package mnstudio.apps.mnstreamx.data.caption

import mnstudio.apps.mnstreamx.domain.model.SpeechText
import mnstudio.apps.mnstreamx.domain.model.CaptionStage
import mnstudio.apps.mnstreamx.domain.caption.CaptionDeadlineException
import mnstudio.apps.mnstreamx.domain.caption.CaptionResourcePolicy
import java.util.concurrent.TimeoutException
import mnstudio.apps.mnstreamx.domain.repository.SpeechEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import android.util.Log

/** Calls and close run on the serial caption worker; cancel may be called on the main thread. */
internal class WhisperSpeechEngine(model: File) : SpeechEngine {
    private val native = NativeWhisper()
    private val lock = Any()
    private var stateResident = false
    private var weightsResident = true
    private val loadStarted = System.nanoTime()
    private var handle = native.create(model.absolutePath, (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2))
    init {
        Log.i("MNX.CaptionPerf", "whisper_load_ms=${(System.nanoTime() - loadStarted) / 1_000_000}")
        if (handle == 0L) throw IOException("Speech model could not be loaded")
    }
    override val memoryStage get() = when {
        stateResident -> CaptionResourcePolicy.LOCAL_WARM
        weightsResident -> CaptionResourcePolicy.TINY_STATE
        else -> CaptionResourcePolicy.TINY_COLD
    }
    override suspend fun transcribe(samples: FloatArray, language: String): SpeechText = transcribe(samples, language, false)
    override suspend fun transcribe(samples: FloatArray, language: String, translateToEnglish: Boolean): SpeechText {
        return decode(samples, language)
    }
    override suspend fun transcribe(samples: FloatArray, language: String, target: String): SpeechText =
        decode(samples, language)
    private suspend fun decode(samples: FloatArray, language: String): SpeechText {
        currentCoroutineContext().ensureActive()
        val pointer = synchronized(lock) { handle }
        check(pointer != 0L)
        weightsResident = true
        val started = System.nanoTime()
        val detectionOnly = language == "auto"
        val rows = try {
            if (detectionOnly) native.detectLanguage(pointer, samples)
            else native.decode(pointer, samples, language, false, "")
        }
        catch (_: TimeoutException) {
            Log.w("MNX.CaptionPerf", "whisper_timeout_ms=${(System.nanoTime() - started) / 1_000_000}")
            currentCoroutineContext().ensureActive()
            throw CaptionDeadlineException(CaptionStage.SPEECH, (System.nanoTime() - started) / 1000000, 10000)
        }
        currentCoroutineContext().ensureActive()
        if (rows.size != 4) throw IOException("Invalid local speech result")
        stateResident = true
        val elapsed = (System.nanoTime() - started) / 1000000
        Log.i("MNX.CaptionPerf", "whisper_decode_ms=$elapsed audio_ms=${samples.size * 1000L / 16000} mode=${if (detectionOnly) "detect" else "transcribe"}")
        if (elapsed > 10000) throw CaptionDeadlineException(CaptionStage.SPEECH, elapsed, 10000)
        return SpeechText(rows[0], rows[3].trim().take(2000), rows[1].toLongOrNull() ?: 0,
            rows[2].toLongOrNull() ?: samples.size * 1000L / 16000, languageOnly = detectionOnly)
    }
    override fun cancel() { synchronized(lock) { if (handle != 0L) native.cancel(handle) } }
    override fun recoverAfterDeadline() {
        val pointer = synchronized(lock) { handle }
        if (pointer != 0L) native.resetState(pointer)
        stateResident = false
    }
    override fun releaseWorkingMemory() {
        val pointer = synchronized(lock) { handle }
        try { if (pointer != 0L) native.park(pointer) }
        finally { stateResident = false; weightsResident = false }
    }
    override fun close() {
        val pointer = synchronized(lock) { handle.also { handle = 0L } }
        stateResident = false
        weightsResident = false
        if (pointer != 0L) native.release(pointer)
    }
}