package com.yourapp.iptv.ui.screen.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBackActionTest {
    @Test fun detailsAreClosedBeforeAnythingBehindThem() {
        assertEquals(PlayerBackAction.CLOSE_DETAILS, playerBackAction(true, true, true, true))
    }
    @Test fun channelListAndNativeControlsKeepTheirExistingPriority() {
        assertEquals(PlayerBackAction.CLOSE_CHANNELS, playerBackAction(false, true, true, true))
        assertEquals(PlayerBackAction.HIDE_CONTROLS, playerBackAction(false, false, true, true))
    }
    @Test fun focusedActionsCloseBeforeExitingThePlayer() {
        assertEquals(PlayerBackAction.HIDE_INFO, playerBackAction(false, false, false, true))
        assertEquals(PlayerBackAction.EXIT, playerBackAction(false, false, false, false))
    }
}