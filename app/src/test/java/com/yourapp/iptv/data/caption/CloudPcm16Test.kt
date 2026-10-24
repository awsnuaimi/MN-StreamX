package com.yourapp.iptv.data.caption

import org.junit.Assert.*
import org.junit.Test

class CloudPcm16Test {
    @Test fun signedLittleEndianPcm16ClampsOutOfRangeSamples() {
        val output = CloudPcm16.encode(floatArrayOf(0f, 1f, -1f, 0.5f, Float.NaN, 2f))
        assertArrayEquals(byteArrayOf(0, 0, -1, 127, 1, -128, -1, 63, 0, 0, -1, 127), output)
    }
    @Test fun rejectsUnboundedAudioWindows() {
        try { CloudPcm16.encode(FloatArray(16000 * 6)); fail("unbounded audio") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun languagesUseStableSpeechLocales() {
        assertEquals("pl-PL", AzureSpeechLanguages.sourceTag("pl"))
        assertEquals("ar", AzureSpeechLanguages.targetTag("ar"))
        assertEquals(5, AzureSpeechLanguages.autoCandidates().distinct().size)
    }
}