package com.yourapp.iptv.data.caption

import org.junit.Assert.*
import org.junit.Test

class MarianTokenIdsTest {
    private val vocabulary = mapOf(">>pol<<" to 44, ">>ara<<" to 4, "word" to 8, "<unk>" to 1)
    @Test fun targetPrefixAndEosAreIndependentAtomicIds() {
        assertArrayEquals(longArrayOf(44, 8, 0), MarianTokenIds.encode(arrayOf("word"), vocabulary, 1, 0, ">>pol<<"))
        assertArrayEquals(longArrayOf(4, 8, 7), MarianTokenIds.encode(arrayOf("word"), vocabulary, 1, 7, ">>ara<<"))
    }
    @Test fun unprefixedModelsDoNotReceiveAnInventedLanguageToken() {
        assertArrayEquals(longArrayOf(8, 1, 7), MarianTokenIds.encode(arrayOf("word", "missing"), vocabulary, 1, 7, null))
    }
    @Test(expected = NoSuchElementException::class)
    fun aMissingRequiredLanguageTokenFailsInsteadOfGeneratingAnotherLanguage() {
        MarianTokenIds.encode(arrayOf("word"), vocabulary, 1, 0, ">>unknown<<")
    }
    @Test(expected = IllegalArgumentException::class)
    fun overlongSentencesAreNotSilentlyTruncated() {
        MarianTokenIds.encode(Array(191) { "word" }, vocabulary, 1, 0, null)
    }
}