package com.yourapp.iptv.ui.screen.player

import com.yourapp.iptv.ui.component.player.*
import org.junit.Assert.*
import org.junit.Test

class PlayerPanelStateTest {
    @Test fun initialPlaybackAndSelectionDoNotOpenCards() {
        assertEquals(PlayerPanel.WATCHING, PlayerPanelState().panel)
        assertEquals(PlayerPanel.WATCHING, PlayerPanelState().cards().afterSelection().panel)
        assertEquals(PlayerPanel.WATCHING, PlayerPanelState().afterZapping().panel)
    }
    @Test fun explicitlyOpenedCardsStayOpenDuringRepeatedZapping() {
        var state = PlayerPanelState().cards()
        repeat(20) { state = state.afterZapping(); assertEquals(PlayerPanel.CARDS, state.panel) }
        assertEquals(PlayerPanel.WATCHING, state.afterSelection().panel)
    }
    @Test fun nestedMoreDetailsAndCardsHavePredictableBackAndFocusRestoration() {
        var state = PlayerPanelState().cards().more().details()
        assertTrue(state.isModal)
        state = state.closeTop(); assertEquals(PlayerPanel.MORE, state.panel)
        state = state.closeTop(); assertEquals(PlayerPanel.CARDS, state.panel); assertTrue(state.focusMore)
        state = state.closeTop(); assertEquals(PlayerPanel.WATCHING, state.panel); assertFalse(state.isModal)
    }
    @Test fun channelListReturnsToItsActualCaller() {
        assertEquals(PlayerPanel.WATCHING, PlayerPanelState().channels().closeTop().panel)
        assertEquals(PlayerPanel.MORE, PlayerPanelState().cards().more().channels().closeTop().panel)
        assertEquals(PlayerPanel.CARDS, PlayerPanelState().cards().channels().closeTop().panel)
    }
    @Test fun historyIsModalAndBackReturnsToMoreThenCards() {
        val history = PlayerPanelState().cards().more().history()
        assertTrue(history.isModal)
        assertEquals(PlayerPanel.MORE, history.closeTop().panel)
        assertEquals(PlayerPanel.CARDS, history.closeTop().closeTop().panel)
        assertEquals(PlayerPanel.WATCHING, history.afterSelection().panel)
    }
    @Test fun repeatedDownAndUpPressesImplementTheWholeVisibilitySequence() {
        var state = PlayerPanelState(); var next = 0; var previous = 0
        val keys = PlayerRemoteKeyGate()
        fun press(code: Int) {
            fun send(action: Int) = keys.handle(code, action, 0, false, state.isModal, state.panel == PlayerPanel.CARDS) {
                when (it) {
                    PlayerRemoteCommand.OPEN_CARDS -> state = state.cards()
                    PlayerRemoteCommand.HIDE_CARDS -> state = state.watching()
                    PlayerRemoteCommand.NEXT -> { next++; state = state.afterZapping() }
                    PlayerRemoteCommand.PREVIOUS -> { previous++; state = state.afterZapping() }
                    else -> Unit
                }
            }
            assertTrue(send(0)); assertTrue(send(1))
        }
        press(20); assertEquals(PlayerPanel.CARDS, state.panel); assertEquals(0, next)
        press(20); press(20); assertEquals(2, next); assertEquals(PlayerPanel.CARDS, state.panel)
        press(19); assertEquals(PlayerPanel.WATCHING, state.panel); assertEquals(0, previous)
        press(19); press(19); assertEquals(2, previous); assertEquals(PlayerPanel.WATCHING, state.panel)
    }
}