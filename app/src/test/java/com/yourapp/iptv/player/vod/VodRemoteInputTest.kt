package com.yourapp.iptv.player.vod

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class VodRemoteInputTest {
    private val input = VodRemoteInput()
    private val commands = mutableListOf<VodRemoteCommand>()
    private fun key(code: Int, action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0, shown: Boolean = false) =
        input.handle(code,action,repeat,shown,commands::add)
    @Test fun openingControlsOwnsTheReleaseRatherThanClickingTheNewFocusedButton() {
        assertTrue(key(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(key(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_UP,shown = true))
        assertEquals(listOf(VodRemoteCommand.SHOW),commands)
        assertFalse(key(KeyEvent.KEYCODE_DPAD_CENTER,shown = true))
    }
    @Test fun heldSeekPressesContinueSeekingAfterControlsAppear() {
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_RIGHT,repeat = 1,shown = true)
        key(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_UP,shown = true)
        assertEquals(listOf(VodRemoteCommand.FORWARD,VodRemoteCommand.FORWARD),commands)
        assertFalse(key(KeyEvent.KEYCODE_DPAD_RIGHT,shown = true))
    }
    @Test fun mediaToggleNeverRepeatsWhileTheRemoteButtonIsHeld() {
        key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,shown = true)
        key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,repeat = 5,shown = true)
        key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.ACTION_UP,shown = true)
        assertEquals(listOf(VodRemoteCommand.TOGGLE),commands)
    }
    @Test fun lifecycleResetAndUnrelatedKeysDoNotKeepAnOldPressAlive() {
        key(KeyEvent.KEYCODE_DPAD_DOWN);input.clear()
        assertFalse(key(KeyEvent.KEYCODE_BACK,shown = true))
        assertFalse(key(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_UP,shown = true))
    }
}