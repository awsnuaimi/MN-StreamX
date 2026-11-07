package mnstudio.apps.mnstreamx.data.caption

import mnstudio.apps.mnstreamx.domain.caption.CaptionLanguageRoute
import mnstudio.apps.mnstreamx.domain.caption.CaptionResourcePolicy
import mnstudio.apps.mnstreamx.domain.model.CaptionLanguage
internal object CaptionSpeechPlan {
    fun memoryStage(source: String) = when (source) {
        "ar", "de", "en" -> CaptionResourcePolicy.MOONSHINE_COLD
        "hi" -> CaptionResourcePolicy.VOSK_COLD
        else -> CaptionResourcePolicy.BASE_COLD
    }
    fun speechBytes(source: String): Long = when (source) {
        "ar", "de", "en" -> MoonshineCatalog.forSource(source).bytes
        "hi" -> VoskHindiCatalog.bundle.bytes
        else -> CaptionModelCatalog.speech.bytes + CaptionModelCatalog.vad.bytes
    }
    fun downloadBytes(source: String, target: String): Long {
        fun textBytes(language: String) = CaptionLanguageRoute.steps(language, target)
            .sumOf { (from, to) -> BergamotCatalog.pack(from, to).bundle.bytes }
        val text = if (source == "auto") CaptionLanguage.entries.maxOf { textBytes(it.tag) } else textBytes(source)
        val speech = if (source == "auto") speechBytes("auto") + listOf("ar", "de", "en", "hi").maxOf(::speechBytes) else speechBytes(source)
        return speech + text
    }
}