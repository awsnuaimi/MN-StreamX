package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class EpgScheduleTest {
    private val first = Program("First", 1000, 2000)
    private val second = Program("Second", 2000, 3000)
    private val schedule = EpgSchedule(listOf(second, first))

    @Test fun exactBoundaryMovesToTheNewProgram() {
        assertEquals(first, schedule.current(1999))
        assertEquals(second, schedule.current(2000))
        assertEquals(second, schedule.next(1999))
        assertNull(schedule.next(2000))
        assertNull(schedule.current(3000))
    }

    @Test fun gapsAndFutureOnlySchedulesDoNotPretendAProgramIsPlaying() {
        assertNull(schedule.current(999))
        assertEquals(first, schedule.next(999))
        val gap = EpgSchedule(listOf(first, Program("Later", 4000, 5000)))
        assertNull(gap.current(3500))
        assertEquals("Later", gap.next(3500)?.title)
    }

    @Test fun overlappingListingsChooseTheLatestActiveProgramAndARealNextProgram() {
        val overlapping = EpgSchedule(listOf(first, Program("Updated", 1500, 2500),
            second, Program("Next", 3000, 4000)))
        assertEquals("Updated", overlapping.current(1750)?.title)
        assertEquals("Next", overlapping.next(1750)?.title)
    }

    @Test fun progressIsCorrectAndAnEmptyScheduleIsSafe() {
        assertEquals(0.5f, schedule.progress(1500), 0.001f)
        assertEquals(0f, schedule.progress(3000), 0f)
        assertNull(EpgSchedule(emptyList()).current(1500))
        assertEquals(0f, EpgSchedule(emptyList()).progress(1500), 0f)
    }
}