package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.Channel
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.HexFormat
import com.yourapp.iptv.domain.organization.lexicon.LexiconEntry
import com.yourapp.iptv.domain.organization.lexicon.LexiconMatcher

class OrderingCompatibilityTest {
    @Test fun fasterHexEncodingMatchesStandardSha256ForUnicodeAndBinaryText() {
        val values = listOf("", "PL Łódź", "قنوات عربية", "a\u0000b", "😀") + (0..255).map { it.toChar().toString() }
        for (value in values) {
            val expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
            assertEquals(expected, OrganizationNames.digest(value))
        }
    }

    @Test fun naturalNumbersQualityTiesAndFolderPathsKeepTheirOrder() {
        fun row(id: String, name: String, path: List<String> = listOf("root", "source")) =
            Channel(id, name, streamUrl = "https://unit.test/$id", folderIds = path)
        val input = listOf(row("ten", "TVP 10 HD"), row("sd", "TVP 2 SD"),
            row("hd", "TVP 2 HD"), row("full", "TVP 2 FHD"), row("ultra", "TVP 2 UHD"),
            row("first", "TVP 1 HD"), row("other", "AAA", listOf("other")))
        val sorted = OrganizationChannelOrder.sort(input, input.associate { it.id to OrganizationNames.normalize(it.name) },
            mapOf("root" to 0, "source" to 0, "other" to 1))
        assertEquals(listOf("first", "ultra", "full", "hd", "sd", "ten", "other"), sorted.map { it.id })
    }

    @Test fun lexiconMatchingKeepsTokenBoundariesLongestPhraseAndStableTies() {
        fun entry(id: String, phrase: String) = LexiconEntry(id, id, LexiconEntry.LANGUAGES.associateWith { phrase })
        val long = entry("z", "foo bar")
        val short = entry("a", "verylongword")
        assertEquals("z", LexiconMatcher.match("foo bar verylongword", listOf(short, long))?.id)
        val tie = entry("a", "zip baz")
        val rows = listOf(long, tie)
        assertEquals("a", LexiconMatcher.match("foo bar zip baz", rows)?.id)
        assertEquals("a", LexiconMatcher.match("foo bar zip baz", rows.reversed())?.id)
        assertFalse(LexiconMatcher.contains("skyline", "sky"))
        assertTrue(LexiconMatcher.contains("skyline sky", "sky"))
        assertTrue(LexiconMatcher.contains("", ""))
        assertFalse(LexiconMatcher.contains("sky", ""))
    }
}