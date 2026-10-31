package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class AdaptiveLiveNavigationTest {
    private val context = AdaptiveLiveNavigation.context(null, false, "")!!
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private fun key(n: Int) = n.toString(16).padStart(64, '0')

    @Test fun oneAccidentalWatchDoesNotChooseAnEntryButRepeatedChoicesDo() {
        val once = AdaptiveLiveNavigation().record(context, a)
        assertNull(once.entry(context) { true })
        val twice = once.record(context, a)
        assertEquals(a, twice.entry(context) { true })
        assertNull(twice.entry(context) { false })
        assertNull(twice.entry(null) { true })
    }
    @Test fun unavailableWinnerFallsBackToEligibleEvidenceAndNewHabitsCanOvertakeOldOnes() {
        val learned = AdaptiveLiveNavigation(contexts = linkedMapOf(context to listOf(
            AdaptiveLiveChoice(a, 12), AdaptiveLiveChoice(b, 4))))
        assertEquals(b, learned.entry(context) { it == b })
        var changed = learned
        repeat(6) { changed = changed.record(context, b) }
        assertEquals(b, changed.entry(context) { true })
    }
    @Test fun recencyBreaksEqualScoresWithoutDependingOnCatalogOrder() {
        val learned = AdaptiveLiveNavigation(contexts = mapOf(context to listOf(
            AdaptiveLiveChoice(b, 4), AdaptiveLiveChoice(a, 4))))
        assertEquals(b, learned.entry(context) { true })
    }
    @Test fun searchIsNeverLearnedAndCategoryAndFavoritesContextsCannotCollide() {
        assertNull(AdaptiveLiveNavigation.context(null, false, "news"))
        assertEquals(context, AdaptiveLiveNavigation.context(null, false, "  "))
        val keys = listOf(null, "", "a:b", "أخبار").flatMap { group ->
            listOf(false, true).map { AdaptiveLiveNavigation.context(group, it, "") }
        }
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.all { it!!.matches(Regex("[a-f0-9]{64}")) })
    }
    @Test fun disablePausesLearningAndResetInvalidatesEvenAnEmptyPendingObservation() {
        val learned = AdaptiveLiveNavigation().record(context, a).record(context, a)
        val off = learned.withEnabled(false)
        assertSame(off, off.record(context, b)); assertNull(off.entry(context) { true })
        assertEquals(a, off.withEnabled(true).entry(context) { true })
        val cleared = off.clear()
        assertFalse(cleared.enabled); assertTrue(cleared.contexts.isEmpty()); assertNotEquals(off.epoch, cleared.epoch)
        assertNotEquals(AdaptiveLiveNavigation(), AdaptiveLiveNavigation().clear())
    }
    @Test fun boundsEvictTheLeastRecentContextAndNeverOverflowScores() {
        var learned = AdaptiveLiveNavigation()
        repeat(100) { learned = learned.record(key(it), a) }
        assertEquals(AdaptiveLiveNavigation.MAX_CONTEXTS, learned.contexts.size)
        assertFalse(learned.contexts.containsKey(key(0)))
        learned = learned.record(key(76), b)
        assertEquals(key(76), learned.contexts.keys.first())
        repeat(1000) { learned = learned.record(key(76), b) }
        assertEquals(AdaptiveLiveNavigation.MAX_SCORE, learned.contexts.getValue(key(76)).first().score)
        assertEquals(learned, learned.validate())
        val otherContext = learned.record(key(99), b)
        val touched = otherContext.record(key(76), b)
        assertEquals(key(76), touched.contexts.keys.first())
        // Map equality ignores insertion order: a revision makes recency-only changes durable too.
        assertNotEquals(otherContext, touched)
    }
    @Test fun malformedAndOversizedEvidenceIsRejectedBeforePersistence() {
        for (value in listOf(
            AdaptiveLiveNavigation(epoch = -1),
            AdaptiveLiveNavigation(contexts = mapOf("raw group" to listOf(AdaptiveLiveChoice(a, 4)))),
            AdaptiveLiveNavigation(contexts = mapOf(context to emptyList())),
            AdaptiveLiveNavigation(contexts = mapOf(context to listOf(AdaptiveLiveChoice(a, 0)))),
            AdaptiveLiveNavigation(contexts = mapOf(context to listOf(AdaptiveLiveChoice(a, 13)))),
            AdaptiveLiveNavigation(contexts = mapOf(context to List(2) { AdaptiveLiveChoice(a, 4) })),
            AdaptiveLiveNavigation(contexts = mapOf(context to List(9) { AdaptiveLiveChoice(key(it), 4) })),
            AdaptiveLiveNavigation(contexts = (0..24).associate { key(it) to listOf(AdaptiveLiveChoice(a, 4)) })
        )) assertThrows(IllegalArgumentException::class.java) { value.validate() }
    }
}