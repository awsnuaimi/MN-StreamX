package mnstudio.apps.mnstreamx.player.vod

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
    @Test fun longHeldSeekAcceleratesAndCoalescesRepeatedPlayerCommands() {
        val deltas = mutableListOf<Long>()
        val code = KeyEvent.KEYCODE_DPAD_RIGHT
        for (repeat in 0..20) {
            assertTrue(input.handle(code,KeyEvent.ACTION_DOWN,repeat,repeat > 0) {
                if (it == VodRemoteCommand.FORWARD) deltas += input.seekStepMs
            })
        }
        assertEquals(listOf(10_000L,10_000L,10_000L,10_000L,
            30_000L,30_000L,30_000L,60_000L,60_000L),deltas)
        assertTrue(key(code,KeyEvent.ACTION_UP,shown = true))
        assertEquals(10_000L,input.seekStepMs)
    }
    @Test fun releasingAndRestartingASeekResetsAcceleration() {
        val code = KeyEvent.KEYCODE_MEDIA_REWIND
        key(code,repeat = 0,shown = true)
        key(code,repeat = 12,shown = true)
        assertEquals(60_000L,input.seekStepMs)
        key(code,KeyEvent.ACTION_UP,shown = true)
        key(code,shown = true)
        assertEquals(10_000L,input.seekStepMs)
        input.clear()
        assertEquals(10_000L,input.seekStepMs)
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