package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.organization.lexicon.CountryLexicon
import com.yourapp.iptv.domain.organization.lexicon.GenreLexicon
import com.yourapp.iptv.domain.organization.lexicon.NetworkLexicon
import org.junit.Assert.*
import org.junit.Test

class MultilingualClassificationTest {
    @Test fun explicitPolishDiacriticsAreFoldedForMatchingOnly() {
        assertEquals("kanaly", OrganizationNames.normalizeMatch("Kanały"))
        assertEquals("aescnzz o", OrganizationNames.normalizeMatch("ąęśćńźż ó"))
        assertEquals("kanały", OrganizationNames.normalize("Kanały"))
        assertEquals("اخبار", OrganizationNames.normalizeMatch("أَخْبَار"))
    }

    @Test fun allEightLanguagesExistForEveryCountryAndGenre() {
        assertEquals(33, CountryLexicon.entries.size)
        assertEquals(33, CountryLexicon.entries.map { it.id }.distinct().size)
        assertEquals(8, GenreLexicon.entries.size)
        for (entry in CountryLexicon.entries) {
            assertEquals(8, entry.displayNames.size)
            assertTrue(entry.displayNames.values.all { it.isNotBlank() })
        }
        for (entry in GenreLexicon.entries) {
            assertEquals(8, entry.displayNames.size)
            assertTrue(entry.displayNames.values.all { it.isNotBlank() })
        }
    }

    @Test fun genreExamplesAndStopwordsAreBoundarySafe() {
        assertEquals(FolderLabel.GENERAL, GenreLexicon.find("Ogólnotematyczne"))
        assertEquals(FolderLabel.GENERAL, GenreLexicon.find("كل القنوات"))
        assertEquals(FolderLabel.SPORTS, GenreLexicon.find("Sport"))
        assertEquals(FolderLabel.NEWS, GenreLexicon.find("Nachrichten"))
        assertEquals(FolderLabel.NEWS, GenreLexicon.find("أخبار"))
        for (word in listOf("Kanały", "kanal", "channels", "Sender", "tv", "IPTV")) {
            assertNull(word, GenreLexicon.find(word))
        }
        assertNull(GenreLexicon.find("sportsman"))
    }

    @Test fun networksAreRecognizedWithoutCapturingBeinMovies() {
        assertEquals("bein", NetworkLexicon.find("beINSPORTS2 HD"))
        assertEquals("bein", NetworkLexicon.find("beIN SPORTS 1"))
        assertEquals("sky", NetworkLexicon.find("Sky Sport"))
        assertEquals("mbc", NetworkLexicon.find("MBC 2"))
        assertEquals("dazn", NetworkLexicon.find("DAZN 1"))
        assertTrue(NetworkLexicon.isBeinEntertainment("beIN MOVIES 1"))
        assertNull(NetworkLexicon.find("beIN MOVIES 1"))
    }
    @Test fun everyCountryNameInEveryLanguageResolvesToItsIsoCode() {
        CountryLexicon.entries.forEach { entry ->
            entry.displayNames.forEach { (language, name) ->
                assertEquals("${entry.id} ($language): $name", entry.id, CountryLexicon.find(name)?.id)
            }
        }
    }

    @Test fun allEightLanguageNamesResolveToTheirGenre() {
        GenreLexicon.entries.forEach { entry ->
            entry.displayNames.forEach { (language, name) ->
                assertEquals("${entry.id} ($language): $name", entry.value, GenreLexicon.find(name))
            }
        }
    }

}