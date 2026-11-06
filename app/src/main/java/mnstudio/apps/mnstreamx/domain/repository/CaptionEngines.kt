package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.SpeechText
import mnstudio.apps.mnstreamx.domain.model.CaptionExitReport
import mnstudio.apps.mnstreamx.domain.caption.CaptionResourceCheck
import mnstudio.apps.mnstreamx.domain.caption.CaptionResourcePolicy
import java.io.File

interface SpeechEngine : AutoCloseable {
    suspend fun transcribe(samples: FloatArray, language: String): SpeechText
    suspend fun transcribe(samples: FloatArray, language: String, translateToEnglish: Boolean): SpeechText = transcribe(samples, language)
    suspend fun transcribe(samples: FloatArray, language: String, target: String): SpeechText =
        transcribe(samples, language, language == "auto" || language != target)
    val memoryStage: String get() = CaptionResourcePolicy.SPEECH
    fun releaseWorkingMemory() = Unit
    fun recoverAfterDeadline() = releaseWorkingMemory()
    fun cancel()
    override fun close()
}
fun interface SpeechEngineFactory { suspend fun open(model: File): SpeechEngine }
interface CaptionModels {
    suspend fun prepare(progress: (Int) -> Unit): File
}
interface CaptionTranslator : AutoCloseable {
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