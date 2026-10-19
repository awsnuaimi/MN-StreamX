package com.yourapp.iptv.ui.component.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRemoteInputTest {
    @Test fun onePressSwitchesOnceAndConsumesRepeatAndRelease() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        fun send(action: Int, repeat: Int = 0) = handlePlayerRemoteKey(
            KeyEvent.KEYCODE_CHANNEL_UP, action, repeat, false, false, commands::add)
        assertTrue(send(KeyEvent.ACTION_DOWN))
        assertTrue(send(KeyEvent.ACTION_DOWN, repeat = 2))
        assertTrue(send(KeyEvent.ACTION_UP))
        assertEquals(listOf(PlayerRemoteCommand.NEXT), commands)
    }

    @Test fun directionKeysRemainAvailableToPlaybackControlsAndTheChannelPanel() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        assertFalse(handlePlayerRemoteKey(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN,
            0, true, false, commands::add))
        assertFalse(handlePlayerRemoteKey(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN,
            0, false, true, commands::add))
        assertTrue(commands.isEmpty())
    }

    @Test fun hiddenControlsAllowUpAndDownToSwitchChannels() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        handlePlayerRemoteKey(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN, 0, false, false, commands::add)
        handlePlayerRemoteKey(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN, 0, false, false, commands::add)
        assertEquals(listOf(PlayerRemoteCommand.NEXT, PlayerRemoteCommand.PREVIOUS), commands)
    }

    @Test fun menuAndBackWorkWhileThePanelIsOpen() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        handlePlayerRemoteKey(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_DOWN, 0, false, true, commands::add)
        handlePlayerRemoteKey(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0, false, true, commands::add)
        assertEquals(listOf(PlayerRemoteCommand.TOGGLE_CHANNELS, PlayerRemoteCommand.BACK), commands)
    }

    @Test fun leftOpensTheChannelListOnRemotesWithoutAMenuButton() {
        val commands = mutableListOf<PlayerRemoteCommand>()
        assertTrue(handlePlayerRemoteKey(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN,
            0, false, false, commands::add))
        assertFalse(handlePlayerRemoteKey(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN,
            0, false, true, commands::add))
        assertEquals(listOf(PlayerRemoteCommand.TOGGLE_CHANNELS), commands)
    }

    @Test fun mediaAndSeekKeysAreLeftToTheNativePlayer() {
        assertFalse(handlePlayerRemoteKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.ACTION_DOWN,
            0, false, false) { error("Unexpected command") })
        assertFalse(handlePlayerRemoteKey(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN,
            0, true, false) { error("Unexpected command") })
    }
}