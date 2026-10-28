package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class HomeUpcomingProgramsTest {
    @Test fun currentOverlapsExpiredAndMalformedProgramsNeverEnterUpcomingSlots() {
        val now = 100L
        val current = Program("Current", 0, 200)
        val overlap = Program("Overlap", 150, 250)
        val a = Program("A", 200, 300);val b = Program("B", 300, 400);val c = Program("C", 400, 500)
        val schedule = EpgSchedule(listOf(c, overlap, Program("Malformed", 450, 0), b, current, a))
        assertEquals(listOf(a, b, c), schedule.upcoming(now, 3))
        assertEquals(emptyList<Program>(), schedule.upcoming(now, 0))
    }
    @Test fun unsupportedTimesAndHugeLimitsRemainBounded() {
        val rows = (1..100).map { Program("$it", it * 100L, (it + 1) * 100L) }
        assertEquals(8, EpgSchedule(rows).upcoming(0, Int.MAX_VALUE).size)
        assertTrue(EpgSchedule(rows).upcoming(Long.MAX_VALUE).isEmpty())
        assertTrue(EpgSchedule(rows).upcoming(0, -1).isEmpty())
    }
}