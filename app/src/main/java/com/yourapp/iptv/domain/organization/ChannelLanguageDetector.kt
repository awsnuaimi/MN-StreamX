package com.yourapp.iptv.domain.organization

import java.util.Locale

/** Routing hints from names, not a claim about the language of the audio track. */
internal object ChannelLanguageDetector {
    private val arabicLetters = Regex("[\\p{IsArabic}&&\\p{L}]")
    private val polishLetters = Regex("[ąćęłńóśźż]")
    private val germanLetters = Regex("[äöüß]")
    private val ncPlus = Regex("(?:^|[^\\p{L}\\p{N}])nc\\s*\\+")
    private val polish = Regex("(?:^| )(?:tvp[0-9]*|tvn[0-9]*|polsat[0-9]*|wiadomosci)(?: |$)")
    private val german = Regex("(?:^| )(?:zdf[0-9]*|ard|rtl[0-9]*|prosieben|pro sieben|sat 1|sat1|3sat|das erste)(?: |$)")
    private val arabic = Regex("(?:^| )(?:mbc[0-9]*|rotana|jazeera|aljazeera|al jazeera)(?: |$)")

    fun detect(names: List<String>): String? {
        if (names.isEmpty()) return null
        val counts = mutableMapOf<String, Int>()
        names.forEach { name -> vote(name)?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        val best = counts.maxByOrNull { it.value } ?: return null
        if (counts.values.count { it == best.value } != 1) return null
        return best.key.takeIf { best.value.toLong() * 100 >= names.size.toLong() * 60 }
    }

    private fun vote(name: String): String? {
        val raw = name.lowercase(Locale.ROOT)
        val text = OrganizationNames.normalizeMatch(raw)
        when (text.substringBefore(' ')) {
            "pl" -> return "polish"
            "de" -> return "german"
            "ar" -> return "arabic"
        }
        val candidates = mutableSetOf<String>()
        if (polishLetters.containsMatchIn(raw) || polish.containsMatchIn(text) || ncPlus.containsMatchIn(raw)) candidates += "polish"
        if (germanLetters.containsMatchIn(raw) || german.containsMatchIn(text)) candidates += "german"
        if (arabicLetters.containsMatchIn(raw) || arabic.containsMatchIn(text)) candidates += "arabic"
        return candidates.singleOrNull()
    }
}