package com.yourapp.iptv.domain.model

import org.junit.Assert.*
import org.junit.Test

class EpgProgramSelectionTest {
    private val current = Program("Current", 1000, 2000)
    private val next = Program("Next", 2000, 3000)

    @Test fun keepsTheExplicitlySelectedFutureProgramAsTimeAdvances() {
        val programs = listOf(current, next)
        val selected = EpgProgramSelection.key(next)
        assertEquals(next, EpgProgramSelection.resolve(programs, selected, 1500))
        assertEquals(next, EpgProgramSelection.resolve(programs, selected, 2500))
    }

    @Test fun defaultSelectionMovesToTheNewCurrentProgramAtTheBoundary() {
        assertEquals(current, EpgProgramSelection.resolve(listOf(next, current), null, 1999))
        assertEquals(next, EpgProgramSelection.resolve(listOf(next, current), null, 2000))
    }

    @Test fun missingSelectionsAndGapsFallBackToTheNearestUpcomingProgram() {
        assertEquals(next, EpgProgramSelection.resolve(listOf(next), "removed-key", 1500))
        assertEquals(current, EpgProgramSelection.resolve(listOf(next, current), null, 500))
    }

    @Test fun emptyAndEntirelyPastSchedulesRemainSafe() {
        assertNull(EpgProgramSelection.resolve(emptyList(), "missing", 5000))
        assertEquals(next, EpgProgramSelection.resolve(listOf(next, current), null, 5000))
    }

    @Test fun overlappingProgramsPreferTheLatestActiveStartAndTitleChangesKeepTheSelection() {
        val overlap = Program("Updated", 1500, 2500)
        assertEquals(overlap, EpgProgramSelection.resolve(listOf(current, overlap), null, 1750))
        assertEquals(overlap.copy(title = "Renamed"), EpgProgramSelection.resolve(
            listOf(overlap.copy(title = "Renamed")), EpgProgramSelection.key(overlap), 1750))
    }
}