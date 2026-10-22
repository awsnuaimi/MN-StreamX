package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.SpeechText
import com.yourapp.iptv.domain.caption.CaptionResourceCheck
import java.io.File

interface SpeechEngine : AutoCloseable {
    suspend fun transcribe(samples: FloatArray, language: String): SpeechText
    /** Serial worker only: release native weights/buffers before text inference. */
    fun releaseWorkingMemory() = Unit
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
    fun cancel()
    override fun close()
}
fun interface CaptionTranslatorFactory { fun create(): CaptionTranslator }
data class CaptionDeviceStats(val abi: String, val cores: Int, val totalMemoryMb: Long, val availableMemoryMb: Long,
    val availableStorageMb: Long? = null)
fun interface CaptionDevice {
    fun canRun(target: String): Boolean
    fun supported(): Boolean = true
    fun assess(target: String): CaptionResourceCheck = CaptionResourceCheck(canRun(target),
        availableMb = snapshot()?.availableMemoryMb)
    fun snapshot(): CaptionDeviceStats? = null
}