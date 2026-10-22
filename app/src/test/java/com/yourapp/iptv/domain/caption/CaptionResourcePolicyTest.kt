package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionStatus
import org.junit.Assert.*
import org.junit.Test

class CaptionResourcePolicyTest {
    private val mb = CaptionResourcePolicy.MIB
    @Test fun screenshotDeviceCanEnterEachStageWhenAndroidHasEnoughHeadroom() {
        for (stage in listOf(CaptionResourcePolicy.SPEECH, CaptionResourcePolicy.ENCODER, CaptionResourcePolicy.DECODER)) {
            assertTrue(stage, CaptionResourcePolicy.memory(stage, 387 * mb, 128 * mb, false).allowed)
        }
    }
    @Test fun eachNewAllocationReservesAndroidsFloorInAdditionToItsOwnBudget() {
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.DECODER, 280 * mb, 128 * mb, false)
        assertFalse(result.allowed); assertEquals(280L, result.availableMb); assertEquals(320L, result.requiredMb)
    }
    @Test fun androidPressureAlwaysStopsEvenWhenFreeBytesAppearSufficient() {
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, 2048 * mb, 128 * mb, true)
        assertFalse(result.allowed); assertEquals(CaptionStatus.MEMORY_PRESSURE, result.failure)
    }
    @Test fun aHighAndroidThresholdIsNeverBypassedToForceTheScreenshotToPass() {
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.DECODER, 387 * mb, 300 * mb, false)
        assertFalse(result.allowed); assertEquals(492L, result.requiredMb)
    }
    @Test fun runningNeedsOnlyHeadroomAndTheNextSmallAllocation() {
        assertTrue(CaptionResourcePolicy.memory(CaptionResourcePolicy.RUNNING, 200 * mb, 64 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.RUNNING, 100 * mb, 64 * mb, false).allowed)
    }
    @Test fun exactThresholdAndInvalidNegativeAvailableBytesAreRejected() {
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, 224 * mb, 64 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, -1, -1, false).allowed)
    }
    @Test fun excessivelyLargeThresholdDoesNotOverflowIntoAnAdmission() {
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, 2048 * mb, Long.MAX_VALUE, false).allowed)
    }
    @Test fun mappedAndWarmStagesKeepAndroidsHeadroomWithoutChargingForResidentWeightsTwice() {
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH_WARM, 192 * mb, 128 * mb, false).allowed)
        assertTrue(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH_WARM, 193 * mb, 128 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.MAPPED_DECODER, 256 * mb, 128 * mb, false).allowed)
        assertTrue(CaptionResourcePolicy.memory(CaptionResourcePolicy.MAPPED_DECODER, 257 * mb, 128 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.DECODER_WARM, 224 * mb, 128 * mb, false).allowed)
    }
}