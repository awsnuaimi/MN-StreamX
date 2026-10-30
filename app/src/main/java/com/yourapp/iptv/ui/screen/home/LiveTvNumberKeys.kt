package com.yourapp.iptv.ui.screen.home

import android.view.KeyEvent as AndroidKey
import androidx.compose.ui.input.key.KeyEvent
import com.yourapp.iptv.ui.presentation.LiveChannelRow

internal fun liveTvDigit(code: Int): Int? = when (code) {
    in AndroidKey.KEYCODE_0..AndroidKey.KEYCODE_9 -> code - AndroidKey.KEYCODE_0
    in AndroidKey.KEYCODE_NUMPAD_0..AndroidKey.KEYCODE_NUMPAD_9 -> code - AndroidKey.KEYCODE_NUMPAD_0
    else -> null
}

/** Parent preview owns complete digit/OK sequences; ordinary row hold-OK remains untouched when inactive. */
internal fun liveTvNumberKeys(entry: LiveTvNumberEntry, rows: Map<Int, LiveChannelRow>, ready: Boolean,
    longPressMillis: Long, jump: (LiveChannelRow) -> Unit, play: (LiveChannelRow) -> Unit): (KeyEvent) -> Boolean = handler@{ event ->
    val key = event.nativeKeyEvent
    val down = key.action == AndroidKey.ACTION_DOWN
    val up = key.action == AndroidKey.ACTION_UP
    if (!down && !up) return@handler false
    val confirm = key.keyCode in setOf(AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER)
    if (confirm && (entry.active || entry.awaitingRelease)) {
        if (down) entry.down(key.keyCode, key.eventTime, key.repeatCount > 0 || key.isLongPress)
        if (up && entry.up(key.keyCode, key.eventTime, key.isCanceled, longPressMillis)) {
            rows[entry.number]?.let { row -> entry.clear(); play(row) }
        }
        return@handler true
    }
    if (!ready || key.isAltPressed || key.isCtrlPressed || key.isMetaPressed) return@handler false
    val digit = liveTvDigit(key.keyCode)
    if (digit != null) {
        if (down && key.repeatCount == 0 && !key.isCanceled) {
            entry.digit(digit); rows[entry.number]?.let(jump)
        }
        return@handler true
    }
    if (entry.active && key.keyCode in setOf(AndroidKey.KEYCODE_DEL, AndroidKey.KEYCODE_FORWARD_DEL)) {
        if (down) { entry.erase(); rows[entry.number]?.let(jump) }
        return@handler true
    }
    if (entry.active && down && key.keyCode in setOf(AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_DPAD_DOWN,
            AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_DPAD_RIGHT, AndroidKey.KEYCODE_PAGE_UP,
            AndroidKey.KEYCODE_PAGE_DOWN, AndroidKey.KEYCODE_CHANNEL_UP, AndroidKey.KEYCODE_CHANNEL_DOWN)) entry.clear()
    false
}