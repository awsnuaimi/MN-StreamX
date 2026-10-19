package com.yourapp.iptv.ui.component.player

import android.view.KeyEvent

enum class PlayerRemoteCommand { NEXT, PREVIOUS, TOGGLE_CHANNELS, SHOW_INFO, BACK }

fun handlePlayerRemoteKey(
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    controlsVisible: Boolean,
    channelListOpen: Boolean,
    onCommand: (PlayerRemoteCommand) -> Unit
): Boolean {
    val command = when (keyCode) {
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> PlayerRemoteCommand.BACK
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> PlayerRemoteCommand.NEXT
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> PlayerRemoteCommand.PREVIOUS
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> PlayerRemoteCommand.TOGGLE_CHANNELS
        KeyEvent.KEYCODE_INFO -> if (!channelListOpen) PlayerRemoteCommand.SHOW_INFO else null
        KeyEvent.KEYCODE_DPAD_UP -> if (!controlsVisible && !channelListOpen) PlayerRemoteCommand.NEXT else null
        KeyEvent.KEYCODE_DPAD_DOWN -> if (!controlsVisible && !channelListOpen) PlayerRemoteCommand.PREVIOUS else null
        KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controlsVisible && !channelListOpen) PlayerRemoteCommand.SHOW_INFO else null
        KeyEvent.KEYCODE_DPAD_LEFT -> if (!controlsVisible && !channelListOpen) PlayerRemoteCommand.TOGGLE_CHANNELS else null
        else -> null
    } ?: return false
    return when (action) {
        KeyEvent.ACTION_DOWN -> {
            if (repeatCount == 0) onCommand(command)
            true
        }
        KeyEvent.ACTION_UP -> true
        else -> false
    }
}