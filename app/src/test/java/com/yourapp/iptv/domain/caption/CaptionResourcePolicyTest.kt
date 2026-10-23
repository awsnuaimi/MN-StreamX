package com.yourapp.iptv.domain.caption

import com.yourapp.iptv.domain.model.CaptionStatus
import com.yourapp.iptv.domain.model.CaptionStage
import org.junit.Assert.*
import org.junit.Test

class CaptionResourcePolicyTest {
    private val mb = CaptionResourcePolicy.MIB
    @Test fun screenshotDeviceCanEnterEachStageWhenAndroidHasEnoughHeadroom() {
        for (stage in listOf(CaptionResourcePolicy.SPEECH, CaptionResourcePolicy.ENCODER, CaptionResourcePolicy.DECODER)) {
            assertTrue(stage, CaptionResourcePolicy.memory(stage, 387 * mb, 128 * mb, false).allowed)
        }
    }
    @Test fun decoderStillRequiresItsAllocationAndPlaybackReserve() {
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.DECODER, 280 * mb, 128 * mb, false)
        assertFalse(result.allowed); assertEquals(280L, result.availableMb); assertEquals(288L, result.requiredMb)
    }
    @Test fun androidPressureAlwaysStopsEvenWhenFreeBytesAppearSufficient() {
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, 2048 * mb, 128 * mb, true)
        assertFalse(result.allowed); assertEquals(CaptionStatus.MEMORY_PRESSURE, result.failure)
    }
    @Test fun aVendorReclaimThresholdIsNotAnAdditionalModelAllocation() {
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.DECODER, 387 * mb, 300 * mb, false)
        assertTrue(result.allowed); assertEquals(288L, result.requiredMb); assertEquals(300L, result.systemThresholdMb)
    }
    @Test fun runningNeedsOnlyHeadroomAndTheNextSmallAllocation() {
        assertTrue(CaptionResourcePolicy.memory(CaptionResourcePolicy.RUNNING, 200 * mb, 64 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.RUNNING, 100 * mb, 64 * mb, false).allowed)
    }
    @Test fun exactThresholdAndInvalidNegativeAvailableBytesAreRejected() {
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, 224 * mb, 64 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, -1, -1, false).allowed)
    }
    @Test fun aLargeVendorThresholdDoesNotChangeTheAppBudgetOrOverflowDiagnostics() {
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, 800 * mb, Long.MAX_VALUE, false)
        assertTrue(result.allowed); assertEquals(224L, result.requiredMb)
        assertTrue(result.systemThresholdMb!! > 0)
    }
    @Test fun mappedAndWarmStagesKeepTheirExistingIncrementalEstimates() {
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH_WARM, 160 * mb, 128 * mb, false).allowed)
        assertTrue(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH_WARM, 161 * mb, 128 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.MAPPED_DECODER, 224 * mb, 128 * mb, false).allowed)
        assertTrue(CaptionResourcePolicy.memory(CaptionResourcePolicy.MAPPED_DECODER, 225 * mb, 128 * mb, false).allowed)
        assertFalse(CaptionResourcePolicy.memory(CaptionResourcePolicy.DECODER_WARM, 192 * mb, 128 * mb, false).allowed)
    }
    @Test fun reported800And1000MiBAreAdmittedOnlyWithoutSystemPressure() {
        val stages = listOf(CaptionResourcePolicy.SPEECH, CaptionResourcePolicy.ENCODER,
            CaptionResourcePolicy.DECODER, CaptionResourcePolicy.MAPPED_DECODER,
            CaptionResourcePolicy.SPEECH_WARM, CaptionResourcePolicy.DECODER_WARM)
        for (available in listOf(800L, 1000L)) for (stage in stages) {
            val ok = CaptionResourcePolicy.memory(stage, available * mb, 900 * mb, false)
            assertTrue("$stage at $available MiB", ok.allowed)
            assertEquals(900L, ok.systemThresholdMb)
            val pressure = CaptionResourcePolicy.memory(stage, available * mb, 900 * mb, true)
            assertFalse(pressure.allowed)
            assertEquals(CaptionStatus.MEMORY_PRESSURE, pressure.failure)
        }
    }
    @Test fun speechBudgetIsSeparateFromThe900MiBThresholdAndNeverShownAs1028() {
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, 1000 * mb, 900 * mb, false)
        assertEquals(224L, result.requiredMb) // Unchanged 128 MiB estimate + unchanged 96 MiB reserve.
        assertEquals(900L, result.systemThresholdMb)
        assertEquals(CaptionStage.SPEECH, result.stage)
    }
    @Test fun aSingleByteAboveTheBudgetIsAcceptedButNegativeAvailableIsNot() {
        assertTrue(CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, 224 * mb + 1, 128 * mb, false).allowed)
        val result = CaptionResourcePolicy.memory(CaptionResourcePolicy.SPEECH, Long.MIN_VALUE, -1, false)
        assertFalse(result.allowed); assertEquals(0L, result.availableMb); assertEquals(0L, result.systemThresholdMb)
    }
}