package com.yourapp.iptv.ui.component.player

import android.view.KeyEvent

enum class PlayerRemoteCommand { NEXT, PREVIOUS, OPEN_CARDS, HIDE_CARDS, TOGGLE_CHANNELS, SHOW_INFO, BACK }

fun handlePlayerRemoteKey(
    keyCode: Int, action: Int, repeatCount: Int,
    controlsVisible: Boolean, channelListOpen: Boolean, cardsVisible: Boolean = false,
    onCommand: (PlayerRemoteCommand) -> Unit
): Boolean {
    val blocked = controlsVisible || channelListOpen
    val command = when (keyCode) {
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> PlayerRemoteCommand.BACK
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> PlayerRemoteCommand.NEXT
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> PlayerRemoteCommand.PREVIOUS
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> PlayerRemoteCommand.TOGGLE_CHANNELS
        KeyEvent.KEYCODE_INFO -> if (!channelListOpen) PlayerRemoteCommand.SHOW_INFO else null
        KeyEvent.KEYCODE_DPAD_UP -> if (blocked) null else
            if (cardsVisible) PlayerRemoteCommand.HIDE_CARDS else PlayerRemoteCommand.PREVIOUS
        KeyEvent.KEYCODE_DPAD_DOWN -> if (blocked) null else
            if (cardsVisible) PlayerRemoteCommand.NEXT else PlayerRemoteCommand.OPEN_CARDS
        KeyEvent.KEYCODE_DPAD_RIGHT -> if (!blocked && !cardsVisible) PlayerRemoteCommand.SHOW_INFO else null
        KeyEvent.KEYCODE_DPAD_LEFT -> if (!blocked && !cardsVisible) PlayerRemoteCommand.TOGGLE_CHANNELS else null
        else -> null
    } ?: return false
    return when (action) {
        KeyEvent.ACTION_DOWN -> { if (repeatCount == 0) onCommand(command); true }
        KeyEvent.ACTION_UP -> true
        else -> false
    }
}

/** Own a handled press until release, even if it opens or closes a focusable panel. */
class PlayerRemoteKeyGate {
    private val held = mutableSetOf<Int>()
    fun reset() { held.clear() }
    fun handle(keyCode: Int, action: Int, repeatCount: Int,
        controlsVisible: Boolean, channelListOpen: Boolean, cardsVisible: Boolean,
        onCommand: (PlayerRemoteCommand) -> Unit): Boolean {
        if (keyCode in held) {
            if (action == KeyEvent.ACTION_UP) held.remove(keyCode)
            return true
        }
        val handled = handlePlayerRemoteKey(keyCode, action, repeatCount,
            controlsVisible, channelListOpen, cardsVisible, onCommand)
        if (handled && action == KeyEvent.ACTION_DOWN) held.add(keyCode)
        return handled
    }
}