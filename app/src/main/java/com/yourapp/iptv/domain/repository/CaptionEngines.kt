package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.SpeechText
import java.io.File

interface SpeechEngine : AutoCloseable {
    suspend fun transcribe(samples: FloatArray, language: String): SpeechText
    fun cancel()
    override fun close()
}
fun interface SpeechEngineFactory { suspend fun open(model: File): SpeechEngine }
interface CaptionModels {
    suspend fun prepare(progress: (Int) -> Unit): File
}
interface CaptionTranslator : AutoCloseable {
    suspend fun prepare(target: String, progress: (Int) -> Unit)
    suspend fun translate(text: String): String
    fun cancel()
    override fun close()
}
fun interface CaptionTranslatorFactory { fun create(): CaptionTranslator }
data class CaptionDeviceStats(val abi: String, val cores: Int, val totalMemoryMb: Long, val availableMemoryMb: Long)
fun interface CaptionDevice {
    fun canRun(target: String): Boolean
    fun snapshot(): CaptionDeviceStats? = null
}