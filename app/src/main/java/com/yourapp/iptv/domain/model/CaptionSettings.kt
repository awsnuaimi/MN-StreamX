package com.yourapp.iptv.domain.model

enum class CaptionMode { OFF, EMBEDDED, GENERATED }
enum class CaptionLanguage(val tag: String, val nativeName: String) {
    ARABIC("ar", "العربية"), ENGLISH("en", "English"), GERMAN("de", "Deutsch"),
    POLISH("pl", "Polski"), HINDI("hi", "हिन्दी");
    companion object { fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag?.substringBefore('-') } }
}
enum class CaptionTextSize(val sp: Int) { SMALL(22), MEDIUM(28), LARGE(34) }
data class CaptionSettings(val mode: CaptionMode = CaptionMode.OFF,
    val target: CaptionLanguage = CaptionLanguage.ARABIC, val source: String = "auto",
    val textSize: CaptionTextSize = CaptionTextSize.MEDIUM, val shaded: Boolean = true) {
    fun normalized() = copy(source = source.takeIf { it == "auto" || CaptionLanguage.fromTag(it) != null } ?: "auto")
}
data class SpeechText(val language: String, val text: String, val startMillis: Long = 0, val endMillis: Long = 0)
data class CaptionCue(val text: String, val startUs: Long, val endUs: Long, val producedAtMillis: Long)
enum class CaptionStatus { OFF, EMBEDDED, PREPARING, DOWNLOADING, LOADING, LISTENING, TRANSLATING,
    SLOW_DEVICE, LOW_MEMORY, LOW_STORAGE, MEMORY_PRESSURE, UNAVAILABLE, UNSUPPORTED_LANGUAGE, FAILED, ADAPTING }
data class CaptionProgress(val status: CaptionStatus = CaptionStatus.OFF, val percent: Int? = null,
    val availableMb: Long? = null, val requiredMb: Long? = null,
    val stage: CaptionStage? = null, val elapsedMillis: Long? = null, val budgetMillis: Long? = null,
    val systemThresholdMb: Long? = null)