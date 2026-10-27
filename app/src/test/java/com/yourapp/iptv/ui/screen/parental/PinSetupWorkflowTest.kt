package com.yourapp.iptv.ui.screen.parental

import org.junit.Assert.*
import org.junit.Test

class PinSetupWorkflowTest {
    @Test fun abandoningSetupWipesBothTransientBuffers() {
        val workflow = PinSetupWorkflow();val old = "123456".toCharArray();val fresh = "222222".toCharArray()
        workflow.setOld(old);workflow.setNew(fresh);workflow.clear()
        assertTrue(old.all { it == '\u0000' });assertTrue(fresh.all { it == '\u0000' })
    }
    @Test fun confirmingWipesTheRepeatedInputAndTransfersOwnershipOfTheOriginalOnly() {
        val workflow = PinSetupWorkflow();val fresh = "222222".toCharArray();val repeat = fresh.copyOf()
        workflow.setNew(fresh);assertTrue(workflow.matches(repeat));assertTrue(repeat.all { it == '\u0000' })
        val transferred = workflow.takeNew();workflow.clear()
        assertSame(fresh,transferred);assertArrayEquals("222222".toCharArray(),transferred)
        transferred.fill('\u0000')
    }
}