package com.yourapp.iptv.ui.component.player

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class PlayerRemoteInputTest {
    @Test fun onePressSwitchesOnceAndConsumesRepeatAndRelease() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        fun send(action: Int, repeat: Int = 0) = handlePlayerRemoteKey(
            KeyEvent.KEYCODE_CHANNEL_UP, action, repeat, false, false, false, commands::add)
        assertTrue(send(KeyEvent.ACTION_DOWN)); assertTrue(send(KeyEvent.ACTION_DOWN, 2)); assertTrue(send(KeyEvent.ACTION_UP))
        assertEquals(listOf(PlayerRemoteCommand.NEXT), commands)
    }
    @Test fun directionsRemainAvailableToNativeControlsAndDialogs() {
        for (code in listOf(19, 20, 21, 22)) {
            assertFalse(handlePlayerRemoteKey(code, 0, 0, true, false, true) { fail() })
            assertFalse(handlePlayerRemoteKey(code, 0, 0, false, true, true) { fail() })
        }
    }
    @Test fun arrowsUseCardVisibilityWithoutATimingWindow() {
        fun command(code: Int, cards: Boolean): PlayerRemoteCommand? {
            var value: PlayerRemoteCommand? = null
            handlePlayerRemoteKey(code, 0, 0, false, false, cards) { value = it }
            return value
        }
        assertEquals(PlayerRemoteCommand.OPEN_CARDS, command(20, false))
        assertEquals(PlayerRemoteCommand.NEXT, command(20, true))
        assertEquals(PlayerRemoteCommand.HIDE_CARDS, command(19, true))
        assertEquals(PlayerRemoteCommand.PREVIOUS, command(19, false))
    }
    @Test fun menuAndBackAreStillRecognizedInTheChannelPanel() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        handlePlayerRemoteKey(82, 0, 0, false, true, false, commands::add)
        handlePlayerRemoteKey(4, 0, 0, false, true, false, commands::add)
        assertEquals(listOf(PlayerRemoteCommand.TOGGLE_CHANNELS, PlayerRemoteCommand.BACK), commands)
    }
    @Test fun leftOpensTheListDuringWatchingAndBrowsesWhenCardsAreOpen() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        assertTrue(handlePlayerRemoteKey(21, 0, 0, false, false, false, commands::add))
        assertFalse(handlePlayerRemoteKey(21, 0, 0, false, false, true, commands::add))
        assertEquals(listOf(PlayerRemoteCommand.TOGGLE_CHANNELS), commands)
    }
    @Test fun rightShowsOnlyInformationDuringWatchingAndBrowsesInsideCards() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        assertTrue(handlePlayerRemoteKey(22, 0, 0, false, false, false, commands::add))
        assertEquals(listOf(PlayerRemoteCommand.SHOW_INFO), commands)
        assertFalse(handlePlayerRemoteKey(22, 0, 0, false, false, true, commands::add))
    }
    @Test fun mediaAndSeekKeysRemainAvailableToTheNativePlayer() {
        assertFalse(handlePlayerRemoteKey(85, 0, 0, false, false, false) { fail() })
        assertFalse(handlePlayerRemoteKey(21, 0, 0, true, false, false) { fail() })
    }
    @Test fun heldDownCannotBecomeANextCommandAfterOpeningCardsEvenIfRepeatCountIsZero() {
        val key = PlayerRemoteKeyGate(); val commands = mutableListOf<PlayerRemoteCommand>()
        assertTrue(key.handle(20, 0, 0, false, false, false, commands::add))
        assertTrue(key.handle(20, 0, 0, false, false, true, commands::add))
        assertTrue(key.handle(20, 0, 10, false, false, true, commands::add))
        assertTrue(key.handle(20, 1, 0, false, false, true, commands::add))
        assertEquals(listOf(PlayerRemoteCommand.OPEN_CARDS), commands)
        key.handle(20, 0, 0, false, false, true, commands::add)
        assertEquals(listOf(PlayerRemoteCommand.OPEN_CARDS, PlayerRemoteCommand.NEXT), commands)
    }
    @Test fun upReleaseCannotReachVideoOrNewlyFocusedControlsAfterHidingCards() {
        val key = PlayerRemoteKeyGate(); val commands = mutableListOf<PlayerRemoteCommand>()
        key.handle(19, 0, 0, false, false, true, commands::add)
        assertTrue(key.handle(19, 0, 1, false, false, false, commands::add))
        assertTrue(key.handle(19, 1, 0, true, true, false, commands::add))
        assertEquals(listOf(PlayerRemoteCommand.HIDE_CARDS), commands)
        key.handle(19, 0, 0, false, false, false, commands::add)
        assertEquals(PlayerRemoteCommand.PREVIOUS, commands.last())
    }
    @Test fun resettingAfterLifecycleStopClearsAReleaseThatWasLost() {
        val key = PlayerRemoteKeyGate(); val commands = mutableListOf<PlayerRemoteCommand>()
        key.handle(20, 0, 0, false, false, false, commands::add); key.reset()
        key.handle(20, 0, 0, false, false, false, commands::add)
        assertEquals(listOf(PlayerRemoteCommand.OPEN_CARDS, PlayerRemoteCommand.OPEN_CARDS), commands)
    }
}