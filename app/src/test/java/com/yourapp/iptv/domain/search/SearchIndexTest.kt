package com.yourapp.iptv.domain.search

import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SearchIndexTest {
    @Test fun arabicDiacriticsAlefFormsAndTatweelAreIgnoredWithoutDestroyingTitles() = runTest {
        val titles = listOf("الإِخْبَارِيَّة", "قناة مُوسـيقى", "آفاق")
        val index = SearchIndex.create(titles, { it }, { it })
        assertEquals(listOf(titles[0]), index.find("الاخبارية").items)
        assertEquals(listOf(titles[1]), index.find("موسيقي").items)
        assertEquals(listOf(titles[2]), index.find("افاق").items)
    }
    @Test fun germanPolishAndCaseDifferencesAreNormalizedUsingARootLocale() = runTest {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val index = SearchIndex.create(listOf("Ärger Straße", "ŁÓDŹ", "INDIGO"), { it }, { it })
            assertEquals(listOf("Ärger Straße"), index.find("arger strasse").items)
            assertEquals(listOf("ŁÓDŹ"), index.find("lodz").items)
            assertEquals(listOf("INDIGO"), index.find("indigo").items)
        } finally { Locale.setDefault(previous) }
    }
    @Test fun exactAndPrefixMatchesComeFirstAndEveryQueryTokenMustMatch() = runTest {
        val titles = listOf("The Great Escape", "Escape from Earth", "Escape", "Great Expectations")
        val index = SearchIndex.create(titles, { it }, { it })
        assertEquals(listOf("Escape", "Escape from Earth", "The Great Escape"), index.find("escape").items)
        assertEquals(listOf("The Great Escape"), index.find("escape great").items)
    }
    @Test fun emptyOrPunctuationOnlyQueriesNeverReturnTheEntireCatalog() = runTest {
        val index = SearchIndex.create(listOf("One", "Two"), { it }, { it })
        assertTrue(index.find(" --- ").items.isEmpty())
        assertTrue(index.find("").items.isEmpty())
    }
    @Test fun duplicateIdsAreRemovedAndResultsStayBounded() = runTest {
        val titles = (1..130).map { "Movie $it" } + "Movie 1"
        val index = SearchIndex.create(titles, { it }, { it })
        assertEquals(130, index.size)
        val results = index.find("movie")
        assertEquals(100, results.items.size); assertTrue(results.more)
        assertEquals(listOf("Movie 1"), index.find("movie 1", 1).items)
    }
    @Test fun oversizedIndexesReportLimitedCoverageInsteadOfClaimingToHaveSearchedEverything() = runTest {
        val index = SearchIndex.create((0..SearchIndex.INDEX_LIMIT).toList(), { it.toString() }, { "Title $it" })
        assertEquals(SearchIndex.INDEX_LIMIT, index.size); assertTrue(index.limited)
    }
    @Test fun constructingAnIndexStopsCooperativelyWhenItsRequestIsCancelled() = runTest {
        val request = Job()
        var visited = 0
        val failure = runCatching {
            withContext(request) {
                SearchIndex.create((1..10_000).toList(), {
                    visited++; if (visited == 130) request.cancel(); it.toString()
                }, { "Title $it" })
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(visited in 130..257)
    }
    @Test fun submittedQueriesAreCleanedAndBounded() {
        assertEquals("One Two", SearchText.query("  One  Two\n "))
        assertEquals(100, SearchText.query("A".repeat(200)).length)
    }
}