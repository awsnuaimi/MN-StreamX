package com.yourapp.iptv.data.caption

internal object AzureSpeechLanguages {
    private val source = mapOf("ar" to "ar-SA", "en" to "en-US", "de" to "de-DE",
        "pl" to "pl-PL", "hi" to "hi-IN")
    fun sourceTag(value: String): String? = source[value]
    // Azure supports up to ten language candidates for continuous detection.
    fun autoCandidates(): List<String> = source.values.toList()
    fun targetTag(value: String): String = value.takeIf { source.containsKey(it) }
        ?: throw IllegalArgumentException("Unsupported translation language")
}