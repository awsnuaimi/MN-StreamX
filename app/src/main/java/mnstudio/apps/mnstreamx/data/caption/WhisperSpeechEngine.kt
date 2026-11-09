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
import mnstudio.apps.mnstreamx.domain.caption.CaptionDiagnosticJournal

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
        CaptionDiagnosticJournal.record("WHISPER_LOAD", (System.nanoTime() - loadStarted) / 1_000_000)
        if (handle != 0L) captureNativeStages(handle, false)
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
        // Whisper full decoding with language="auto" detects the language and
        // transcribes in the SAME request. A detect-only call discarded the
        // first voiced window and required a second expensive inference.
        val automatic = language == "auto"
        val rows = try {
            native.decode(pointer, samples, language, false, "")
        }
        catch (_: TimeoutException) {
            captureNativeStages(pointer, true)
            CaptionDiagnosticJournal.record("WHISPER_TIMEOUT", (System.nanoTime() - started) / 1_000_000)
            Log.w("MNX.CaptionPerf", "whisper_timeout_ms=${(System.nanoTime() - started) / 1_000_000}")
            currentCoroutineContext().ensureActive()
            throw CaptionDeadlineException(CaptionStage.SPEECH, (System.nanoTime() - started) / 1000000, 10000)
        }
        currentCoroutineContext().ensureActive()
        if (rows.size != 4) throw IOException("Invalid local speech result")
        stateResident = true
        val elapsed = (System.nanoTime() - started) / 1000000
        captureNativeStages(pointer, true)
        CaptionDiagnosticJournal.record("WHISPER_DECODE", elapsed,
            "audio_ms=${samples.size * 1000L / 16000},mode=${if (automatic) "auto" else "speech"}")
        Log.i("MNX.CaptionPerf", "whisper_decode_ms=$elapsed audio_ms=${samples.size * 1000L / 16000} mode=${if (automatic) "auto" else "transcribe"}")
        if (elapsed > 10000) throw CaptionDeadlineException(CaptionStage.SPEECH, elapsed, 10000)
        val resultText = rows[3].trim().take(2000)
        CaptionDiagnosticJournal.record("WHISPER_RESULT", elapsed,
            "chars=${resultText.length},detected=${rows[0].take(8)},mode=${if (automatic) "auto" else "speech"}")
        return SpeechText(rows[0], resultText, rows[1].toLongOrNull() ?: 0,
            rows[2].toLongOrNull() ?: samples.size * 1000L / 16000, languageOnly = false)
    }
    private fun captureNativeStages(pointer: Long, includeInference: Boolean) {
        // The native timing accessor is read only after a synchronous JNI operation returns.
        val timings = native.stageTimings(pointer)
        if (timings.size < 3) return
        if (!includeInference && timings[0] >= 0) CaptionDiagnosticJournal.record("WHISPER_MODEL_LOAD", timings[0])
        if (includeInference) {
            if (timings[1] >= 0) CaptionDiagnosticJournal.record("WHISPER_STATE_INIT", timings[1])
            if (timings[2] >= 0) CaptionDiagnosticJournal.record("WHISPER_INFERENCE", timings[2])
            // Record native outcome in the exported report, not just logcat.
            // Negative status codes are expected when the deadline aborts.
            if (timings.size >= 6) CaptionDiagnosticJournal.record("WHISPER_EXIT", timings[2].coerceAtLeast(0),
                "status=${timings[3]},deadline=${timings[4]},ctx=${timings[5]}")
            if (timings.size >= 9) CaptionDiagnosticJournal.record("WHISPER_SEGMENTS", timings[2].coerceAtLeast(0),
                "seen=${timings[6]},kept=${timings[7]},rejected=${timings[8]}")
        }
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