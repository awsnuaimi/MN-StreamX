package mnstudio.apps.mnstreamx.data.caption

import mnstudio.apps.mnstreamx.domain.repository.CaptionModels
import mnstudio.apps.mnstreamx.domain.repository.SpeechEngine
import java.io.File
internal class SourceCaptionModels(private val files: VerifiedModelFiles) : CaptionModels {
    private val hindi = VoskHindiModels(files)
    override fun memoryStage(source: String) = CaptionSpeechPlan.memoryStage(source)
    override suspend fun prepare(progress: (Int) -> Unit) = prepare("auto", progress)
    override suspend fun prepare(source: String, progress: (Int) -> Unit): File = when (source) {
        "ar", "de", "en" -> files.prepare(MoonshineCatalog.forSource(source), progress)
        "hi" -> hindi.prepare(progress)
        else -> {
            val folder = files.prepare(CaptionModelCatalog.speech) { progress(it * 93 / 100) }
            files.prepare(CaptionModelCatalog.vad) { progress(93 + it * 7 / 100) }
            folder.resolve(CaptionModelCatalog.speech.assets.single().path)
        }
    }
    suspend fun open(model: File): SpeechEngine {
        MoonshineCatalog.sourceForFolder(model.name)?.let { return MoonshineSpeechEngine(model, it) }
        if (model.isDirectory && model.name == VoskHindiCatalog.FOLDER) return VoskHindiSpeechEngine(model)
        val vad = files.prepare(CaptionModelCatalog.vad) { }
        val detector = SileroSpeechDetector(vad.resolve(CaptionModelCatalog.vad.assets.single().path))
        try { return DetectedSpeechEngine(WhisperSpeechEngine(model), detector) }
        catch (error: Throwable) { detector.close(); throw error }
    }
}