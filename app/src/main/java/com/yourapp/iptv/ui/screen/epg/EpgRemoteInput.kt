package com.yourapp.iptv.ui.screen.epg

import android.view.KeyEvent

enum class EpgFocusArea { CHANNELS, PROGRAMS, FILTERS }

data class EpgFocusRequest(val area: EpgFocusArea, val serial: Int = 0)

fun handleEpgPaneKey(
    keyCode: Int, action: Int, repeatCount: Int, area: EpgFocusArea, isRtl: Boolean,
    onMove: (EpgFocusArea) -> Unit
): Boolean {
    val towardPrograms = if (isRtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
    val towardChannels = if (isRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
    val destination = when {
        area == EpgFocusArea.CHANNELS && keyCode == towardPrograms -> EpgFocusArea.PROGRAMS
        area == EpgFocusArea.PROGRAMS && keyCode == towardChannels -> EpgFocusArea.CHANNELS
        else -> return false
    }
    return when (action) {
        KeyEvent.ACTION_DOWN -> { if (repeatCount == 0) onMove(destination); true }
        KeyEvent.ACTION_UP -> true
        else -> false
    }
}