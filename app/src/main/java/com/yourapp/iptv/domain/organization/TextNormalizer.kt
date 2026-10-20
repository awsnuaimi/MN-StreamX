package com.yourapp.iptv.domain.organization

import java.text.Normalizer
import java.util.Locale

/** Matching only; persisted identity stays in OrganizationNames.normalize(). */
internal object TextNormalizer {
    private val marks = Regex("\\p{M}+")
    private val words = Regex("[\\p{L}\\p{N}]+")

    fun normalizeMatch(value: String): String {
        val polish = value.lowercase(Locale.ROOT)
            .replace('ł', 'l').replace('ą', 'a').replace('ę', 'e')
            .replace('ś', 's').replace('ć', 'c').replace('ń', 'n')
            .replace('ź', 'z').replace('ż', 'z').replace('ó', 'o')
            .replace("ß", "ss").replace("æ", "ae").replace("œ", "oe").replace('ø', 'o')
        val folded = marks.replace(Normalizer.normalize(polish, Normalizer.Form.NFKD), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه')
        return words.findAll(folded).joinToString(" ") { it.value }
    }
}