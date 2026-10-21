package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.model.SpeechText
import com.yourapp.iptv.domain.repository.SpeechEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/** Calls and close run on the serial caption worker; cancel may be called on the main thread. */
internal class WhisperSpeechEngine(model: File) : SpeechEngine {
    private val native = NativeWhisper()
    private val lock = Any()
    private var handle = native.create(model.absolutePath, (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2))
    init { if (handle == 0L) throw IOException("Speech model could not be loaded") }
    override suspend fun transcribe(samples: FloatArray, language: String): SpeechText {
        currentCoroutineContext().ensureActive()
        val pointer = synchronized(lock) { handle }
        check(pointer != 0L)
        val rows = native.decode(pointer, samples, language)
        currentCoroutineContext().ensureActive()
        if (rows.size != 4) throw IOException("Invalid local speech result")
        return SpeechText(rows[0], rows[3].trim().take(2000), rows[1].toLongOrNull() ?: 0,
            rows[2].toLongOrNull() ?: samples.size * 1000L / 16000)
    }
    override fun cancel() { synchronized(lock) { if (handle != 0L) native.cancel(handle) } }
    override fun close() { synchronized(lock) { if (handle != 0L) { native.release(handle); handle = 0L } } }
}