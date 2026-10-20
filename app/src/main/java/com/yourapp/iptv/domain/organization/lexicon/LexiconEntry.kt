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
    fun contains(text: String, phrase: String): Boolean {
        if (phrase.isEmpty()) return text.isEmpty() || text.startsWith(' ') || text.endsWith(' ') || text.contains("  ")
        var start = text.indexOf(phrase)
        while (start >= 0) {
            val end = start + phrase.length
            if ((start == 0 || text[start - 1] == ' ') && (end == text.length || text[end] == ' ')) return true
            start = text.indexOf(phrase, start + 1)
        }
        return false
    }

    fun <T> match(text: String, entries: List<LexiconEntry<T>>): LexiconEntry<T>? {
        var best: LexiconEntry<T>? = null
        var words = -1
        var length = -1
        for (entry in entries) for (phrase in entry.phrases) {
            if (!contains(text, phrase)) continue
            val count = phrase.count { it == ' ' } + 1
            if (count > words || (count == words && (phrase.length > length ||
                    (phrase.length == length && (best == null || entry.id < best.id))))) {
                best = entry; words = count; length = phrase.length
            }
        }
        return best
    }
}