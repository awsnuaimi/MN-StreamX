package com.yourapp.iptv.ui.screen.home

import android.app.Application
import android.view.KeyEvent as AndroidKey
import androidx.compose.ui.input.key.KeyEvent
import com.yourapp.iptv.ui.presentation.LiveChannelRow
import com.yourapp.iptv.domain.model.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LiveTvNumberKeysTest {
    private val entry = LiveTvNumberEntry()
    private val row = LiveChannelRow("one", "One", listOf(Channel("one", "One", streamUrl = "one")), number = 1)
    private var jumps = 0; private var plays = 0
    private fun event(code: Int, action: Int = AndroidKey.ACTION_DOWN, time: Long = 0, repeat: Int = 0, flags: Int = 0) =
        KeyEvent(AndroidKey(0, time, action, code, repeat, 0, -1, 0, flags))
    private fun handler(ready: Boolean = true) = liveTvNumberKeys(entry, mapOf(1 to row), ready, 500, { jumps++ }, { plays++ })
    @Test fun topRowAndNumpadCodesMapExactlyAndOtherKeysDoNot() {
        repeat(10) { assertEquals(it, liveTvDigit(AndroidKey.KEYCODE_0 + it)); assertEquals(it, liveTvDigit(AndroidKey.KEYCODE_NUMPAD_0 + it)) }
        assertNull(liveTvDigit(AndroidKey.KEYCODE_A)); assertNull(liveTvDigit(AndroidKey.KEYCODE_CHANNEL_UP))
    }
    @Test fun digitRepeatsAndKeyUpNeverAppendOrStartPlayback() {
        val input = handler(); assertTrue(input(event(AndroidKey.KEYCODE_1)))
        input(event(AndroidKey.KEYCODE_1, repeat = 3)); input(event(AndroidKey.KEYCODE_1, AndroidKey.ACTION_UP))
        assertEquals("1", entry.digits); assertEquals(1, jumps); assertEquals(0, plays)
    }
    @Test fun canceledOkAndUnmatchedNumbersCannotPlayThePreviousFocusedRow() {
        val input = handler(); input(event(AndroidKey.KEYCODE_1)); input(event(AndroidKey.KEYCODE_9))
        input(event(AndroidKey.KEYCODE_DPAD_CENTER)); input(event(AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.ACTION_UP, 10))
        assertEquals(0, plays); entry.clear(); input(event(AndroidKey.KEYCODE_1)); input(event(AndroidKey.KEYCODE_ENTER))
        input(event(AndroidKey.KEYCODE_ENTER, AndroidKey.ACTION_UP, 10, flags = AndroidKey.FLAG_CANCELED)); assertEquals(0, plays)
    }
    @Test fun ordinaryInactiveOkIsLeftToExistingRowControls() {
        val input = handler(); assertFalse(input(event(AndroidKey.KEYCODE_DPAD_CENTER)))
        assertFalse(input(event(AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.ACTION_UP))); assertEquals(0, plays)
    }
    @Test fun arrowsEndNumericEntryAndRemainAvailableToNormalNavigation() {
        val input = handler(); input(event(AndroidKey.KEYCODE_1))
        assertFalse(input(event(AndroidKey.KEYCODE_DPAD_DOWN))); assertFalse(entry.active); assertEquals(0, plays)
    }
    @Test fun disabledProjectionCannotAcceptAChannelNumber() {
        val input = handler(false); assertFalse(input(event(AndroidKey.KEYCODE_NUMPAD_1))); assertFalse(entry.active)
    }
    @Test fun expiredConfirmationStillOwnsItsReleaseAndCannotFallThroughToARow() {
        val input = handler(); input(event(AndroidKey.KEYCODE_1)); input(event(AndroidKey.KEYCODE_DPAD_CENTER))
        entry.expire(entry.revision)
        assertTrue(input(event(AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.ACTION_UP, 10))); assertEquals(0, plays)
    }
}