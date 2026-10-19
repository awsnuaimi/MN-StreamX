package com.yourapp.iptv.ui.screen.player

import org.junit.Assert.*
import org.junit.Test

class PlayerBackActionTest {
    @Test fun watchingExitsAndCardsCloseFirst() {
        assertEquals(PlayerBackAction.EXIT, playerBackAction(PlayerPanel.WATCHING))
        assertEquals(PlayerBackAction.HIDE_CARDS, playerBackAction(PlayerPanel.CARDS))
    }
    @Test fun eachDialogOwnsBackInsteadOfExitingThePlayer() {
        assertEquals(PlayerBackAction.CLOSE_DETAILS, playerBackAction(PlayerPanel.DETAILS))
        assertEquals(PlayerBackAction.CLOSE_MORE, playerBackAction(PlayerPanel.MORE))
        assertEquals(PlayerBackAction.CLOSE_CHANNELS, playerBackAction(PlayerPanel.CHANNELS))
    }
    @Test fun nativeControlsCloseBeforeExiting() {
        assertEquals(PlayerBackAction.HIDE_CONTROLS, playerBackAction(PlayerPanel.CONTROLS))
    }
}