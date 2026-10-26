package com.yourapp.iptv.domain.diagnostics

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.*
import org.junit.Assert.*

class DiagnosticJournalTest {
    @Before fun setup() { DiagnosticJournal.clear() }
    @After fun cleanup() { DiagnosticJournal.clear() }
    @Test fun aFailureStormRetainsOnlyTheNewestBoundedEvents() {
        repeat(1000) { DiagnosticJournal.record(DiagnosticArea.PLAYBACK,DiagnosticIssue.PLAYBACK,code = it) }
        val rows = DiagnosticJournal.events.value
        assertEquals(64,rows.size);assertEquals(999,rows.first().code);assertEquals(936,rows.last().code)
    }
    @Test fun arbitraryStringsAndOutOfRangeTechnicalValuesCannotCrossTheJournalBoundary() {
        DiagnosticJournal.record(DiagnosticArea.ACCOUNT,DiagnosticIssue.HTTP,"https://secret.invalid/user/password",Int.MAX_VALUE,-1)
        val row = DiagnosticJournal.events.value.single()
        assertNull(row.sourceKey);assertNull(row.code);assertNull(row.httpStatus)
        DiagnosticJournal.clear();assertTrue(DiagnosticJournal.events.value.isEmpty())
    }
    @Test fun failuresArrivingFromSeveralCallbacksDoNotLoseIdentityOrBreakTheBound() {
        val workers = Executors.newFixedThreadPool(4)
        try {
            repeat(400) { workers.submit { DiagnosticJournal.record(DiagnosticArea.PLAYBACK,DiagnosticIssue.PLAYBACK) } }
            workers.shutdown();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS))
            val rows = DiagnosticJournal.events.value
            assertEquals(64,rows.size);assertEquals(64,rows.map { it.id }.distinct().size)
            assertEquals(rows.map { it.id }.sortedDescending(),rows.map { it.id })
        } finally { workers.shutdownNow() }
    }
}