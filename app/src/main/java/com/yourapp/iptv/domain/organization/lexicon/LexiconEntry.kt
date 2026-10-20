package com.yourapp.iptv.domain.organization.lexicon

import com.yourapp.iptv.domain.organization.TextNormalizer

/** Every entry supplies names for the eight supported lexicon languages. */
internal data class LexiconEntry<T>(
    val id: String,
    val value: T,
    val displayNames: Map<String, String>,
    val aliases: List<String> = emptyList()
) {
    init { require(LANGUAGES.all { !displayNames[it].isNullOrBlank() }) }

    val phrases: List<String> by lazy {
        (displayNames.values + aliases).map(TextNormalizer::normalizeMatch)
            .filter(String::isNotBlank).distinct()
            .sortedWith(compareByDescending<String> { it.split(' ').size }.thenByDescending { it.length }.thenBy { it })
    }

    companion object { val LANGUAGES = setOf("ar", "en", "de", "pl", "fr", "es", "it", "tr") }
}

/** Whole-token phrases, not arbitrary substrings. Longest phrase is considered first. */
internal object LexiconMatcher {
    fun contains(text: String, phrase: String): Boolean =
        (" $text ").contains(" $phrase ")

    fun <T> match(text: String, entries: List<LexiconEntry<T>>): LexiconEntry<T>? =
        entries.asSequence().flatMap { entry -> entry.phrases.asSequence()
            .filter { contains(text, it) }.map { entry to it } }
            .sortedWith(compareByDescending<Pair<LexiconEntry<T>, String>> { it.second.split(' ').size }
                .thenByDescending { it.second.length }.thenBy { it.first.id })
            .firstOrNull()?.first
}