package com.yourapp.iptv.domain.caption

import org.junit.Assert.*
import org.junit.Test

class CaptionStorageCoordinatorTest {
    @Test fun maintenanceRefusesToDeleteWhileSessionIsActive() {
        val gate = CaptionStorageCoordinator()
        assertTrue(gate.beginSession())
        assertFalse(gate.beginMaintenance())
        gate.endSession()
        assertTrue(gate.beginMaintenance())
        assertFalse(gate.beginSession())
        assertFalse(gate.beginMaintenance())
        gate.endMaintenance()
        assertTrue(gate.beginSession())
        gate.endSession()
    }
}