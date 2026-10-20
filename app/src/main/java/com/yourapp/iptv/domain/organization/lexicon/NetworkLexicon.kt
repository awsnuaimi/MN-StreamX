package com.yourapp.iptv.domain.organization.lexicon

import com.yourapp.iptv.domain.organization.OrganizationNames

/** Recognition only. A network without a predefined folder must not create a missing root. */
internal object NetworkLexicon {
    val entries = listOf(
        LexiconEntry("bein", "bein", mapOf("ar" to "بي إن سبورت", "en" to "beIN Sports",
            "de" to "beIN Sport", "pl" to "beIN Sport", "fr" to "beIN Sports", "es" to "beIN Deportes",
            "it" to "beIN Sport", "tr" to "beIN Spor"), listOf("بين سبورت", "بي ان سبورت")),
        LexiconEntry("sky", "sky", mapOf("ar" to "سكاي", "en" to "Sky", "de" to "Sky",
            "pl" to "Sky", "fr" to "Sky", "es" to "Sky", "it" to "Sky", "tr" to "Sky")),
        LexiconEntry("mbc", "mbc", mapOf("ar" to "إم بي سي", "en" to "MBC", "de" to "MBC",
            "pl" to "MBC", "fr" to "MBC", "es" to "MBC", "it" to "MBC", "tr" to "MBC")),
        LexiconEntry("dazn", "dazn", mapOf("ar" to "دازن", "en" to "DAZN", "de" to "DAZN",
            "pl" to "DAZN", "fr" to "DAZN", "es" to "DAZN", "it" to "DAZN", "tr" to "DAZN"))
    )

    private val compactSports = Regex("(?:^| )beinsports?[0-9]*(?: |$)")
    private val compactMovies = Regex("(?:^| )bein(?:movies?|cinema|drama|entertainment)[0-9]*(?: |$)")
    private val movies = Regex("(?:^| )bein (?:movies?|cinema|drama|entertainment)(?: |[0-9]|$)")
    private val numbered = Regex("(?:^| )bein [0-9]+(?: |$)")

    fun find(text: String): String? {
        val normalized = OrganizationNames.normalizeMatch(text)
        return when {
            isBeinEntertainment(normalized) -> null
            compactSports.containsMatchIn(normalized) || numbered.containsMatchIn(normalized) -> "bein"
            else -> LexiconMatcher.match(normalized, entries)?.value
        }
    }

    fun isBeinEntertainment(text: String): Boolean {
        val normalized = OrganizationNames.normalizeMatch(text)
        return movies.containsMatchIn(normalized) || compactMovies.containsMatchIn(normalized)
    }
}