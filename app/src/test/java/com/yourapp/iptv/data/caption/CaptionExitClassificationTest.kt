package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.model.CaptionExitKind
import org.junit.Assert.*
import org.junit.Test

class CaptionExitClassificationTest {
    @Test fun explicitLowMemoryAndNativeCrashRecordsRemainDistinct() {
        assertEquals(CaptionExitKind.LOW_MEMORY, CaptionDeviceInfo.exitKind(3))
        assertEquals(CaptionExitKind.NATIVE_CRASH, CaptionDeviceInfo.exitKind(5))
        assertEquals(CaptionExitKind.JAVA_CRASH, CaptionDeviceInfo.exitKind(4))
        assertEquals(CaptionExitKind.ANR, CaptionDeviceInfo.exitKind(6))
    }
    @Test fun aSignalDoesNotGetMisreportedAsConfirmedLowMemory() {
        assertEquals(CaptionExitKind.SIGNAL, CaptionDeviceInfo.exitKind(2))
        assertNotEquals(CaptionExitKind.LOW_MEMORY, CaptionDeviceInfo.exitKind(2))
    }
    @Test fun normalExitUserStopAndUnknownCodesAreNotShownAsACrash() {
        for (reason in listOf(0, 1, 10, 11, 999)) assertNull(CaptionDeviceInfo.exitKind(reason))
    }
}