package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.model.SpeechText
import com.yourapp.iptv.domain.model.CaptionStage
import com.yourapp.iptv.domain.caption.CaptionDeadlineException
import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import java.util.concurrent.TimeoutException
import com.yourapp.iptv.domain.repository.SpeechEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/** Calls and close run on the serial caption worker; cancel may be called on the main thread. */
internal class WhisperSpeechEngine(model: File) : SpeechEngine {
    private val native = NativeWhisper()
    private val lock = Any()
    private var stateResident = false
    private var handle = native.create(model.absolutePath, (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2))
    init { if (handle == 0L) throw IOException("Speech model could not be loaded") }
    override val memoryStage get() = if (stateResident) CaptionResourcePolicy.SPEECH_WARM else CaptionResourcePolicy.SPEECH
    override suspend fun transcribe(samples: FloatArray, language: String): SpeechText = transcribe(samples, language, true)
    override suspend fun transcribe(samples: FloatArray, language: String, translateToEnglish: Boolean): SpeechText {
        return decode(samples, language, translateToEnglish, "")
    }
    override suspend fun transcribe(samples: FloatArray, language: String, target: String): SpeechText =
        decode(samples, language, language == "auto" || language != target, target)
    private suspend fun decode(samples: FloatArray, language: String, translateToEnglish: Boolean, target: String): SpeechText {
        currentCoroutineContext().ensureActive()
        val pointer = synchronized(lock) { handle }
        check(pointer != 0L)
        val started = System.nanoTime()
        val rows = try { native.decode(pointer, samples, language, translateToEnglish, target) }
        catch (_: TimeoutException) {
            currentCoroutineContext().ensureActive()
            throw CaptionDeadlineException(CaptionStage.SPEECH, (System.nanoTime() - started) / 1000000, 10000)
        }
        currentCoroutineContext().ensureActive()
        if (rows.size != 4) throw IOException("Invalid local speech result")
        stateResident = true
        return SpeechText(rows[0], rows[3].trim().take(2000), rows[1].toLongOrNull() ?: 0,
            rows[2].toLongOrNull() ?: samples.size * 1000L / 16000)
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
        finally { stateResident = false }
    }
    override fun close() {
        val pointer = synchronized(lock) { handle.also { handle = 0L } }
        stateResident = false
        if (pointer != 0L) native.release(pointer)
    }
}