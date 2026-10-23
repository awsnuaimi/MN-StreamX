package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.SpeechText
import com.yourapp.iptv.domain.model.CaptionExitReport
import com.yourapp.iptv.domain.caption.CaptionResourceCheck
import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import java.io.File

interface SpeechEngine : AutoCloseable {
    suspend fun transcribe(samples: FloatArray, language: String): SpeechText
    suspend fun transcribe(samples: FloatArray, language: String, translateToEnglish: Boolean): SpeechText = transcribe(samples, language)
    /** The native engine can decide direct transcription after automatic language detection. */
    suspend fun transcribe(samples: FloatArray, language: String, target: String): SpeechText =
        transcribe(samples, language, language == "auto" || language != target)
    val memoryStage: String get() = CaptionResourcePolicy.SPEECH
    /** Serial worker only: release temporary computation buffers before text inference. */
    fun releaseWorkingMemory() = Unit
    /** Drop an interrupted decoding state without unloading reusable model weights. */
    fun recoverAfterDeadline() = releaseWorkingMemory()
    fun cancel()
    override fun close()
}
fun interface SpeechEngineFactory { suspend fun open(model: File): SpeechEngine }
interface CaptionModels {
    suspend fun prepare(progress: (Int) -> Unit): File
}
interface CaptionTranslator : AutoCloseable {
    /** Only downloads/verifies files. Must not allocate inference sessions. */
    suspend fun download(target: String, progress: (Int) -> Unit) = Unit
    suspend fun prepare(target: String, progress: (Int) -> Unit)
    suspend fun translate(text: String): String
    fun releaseWorkingMemory() = Unit
    fun cancel()
    override fun close()
}
fun interface CaptionTranslatorFactory { fun create(): CaptionTranslator }
data class CaptionDeviceStats(val abi: String, val cores: Int, val totalMemoryMb: Long, val availableMemoryMb: Long,
    val availableStorageMb: Long? = null, val lastExit: CaptionExitReport? = null)
fun interface CaptionDevice {
    fun canRun(target: String): Boolean
    fun supported(): Boolean = true
    fun assess(target: String): CaptionResourceCheck = CaptionResourceCheck(canRun(target),
        availableMb = snapshot()?.availableMemoryMb)
    fun snapshot(): CaptionDeviceStats? = null
}