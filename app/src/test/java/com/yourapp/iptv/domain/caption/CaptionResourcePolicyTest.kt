package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionStatus
import org.junit.Assert.*
import org.junit.Test

class CaptionResourcePolicyTest {
    private val mb = CaptionResourcePolicy.MIB
    @Test fun twoGigabytesOfAvailableRamPassBothEngineBudgets() {
        assertTrue(CaptionResourcePolicy.memory("ar", 2048 * mb, 128 * mb, false).allowed)
        assertTrue(CaptionResourcePolicy.memory("en", 2048 * mb, 128 * mb, false).allowed)
    }
    @Test fun englishFitsWhenTheAdditionalTranslationEngineDoesNot() {
        assertTrue(CaptionResourcePolicy.memory("en", 400 * mb, 64 * mb, false).allowed)
        val result = CaptionResourcePolicy.memory("ar", 400 * mb, 64 * mb, false)
        assertFalse(result.allowed); assertEquals(400L, result.availableMb); assertEquals(576L, result.requiredMb)
    }
    @Test fun systemPressureIsNotReportedAsDiskSpaceOrAMisleadingNumericDeficit() {
        val result = CaptionResourcePolicy.memory("ar", 2048 * mb, 128 * mb, true)
        assertFalse(result.allowed); assertEquals(CaptionStatus.MEMORY_PRESSURE, result.failure)
    }
    @Test fun androidThresholdIsPreservedAlongsideTheEngineBudget() {
        val result = CaptionResourcePolicy.memory("en", 350 * mb, 300 * mb, false)
        assertFalse(result.allowed); assertEquals(364L, result.requiredMb)
    }
    @Test fun runningChecksUseRemainingHeadroomRatherThanTheInitialTwoEngineBudget() {
        assertTrue(CaptionResourcePolicy.memory("running", 200 * mb, 64 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory("running", 100 * mb, 64 * mb, false).allowed)
    }
}