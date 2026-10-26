package com.yourapp.iptv.player.vod

import android.view.KeyEvent

enum class VodRemoteCommand { SHOW, REWIND, FORWARD, TOGGLE, PLAY, PAUSE, NEXT }

/** Own the whole press: showing controls must not turn its key-up into a button click. */
class VodRemoteInput {
    private val held = mutableMapOf<Int,VodRemoteCommand>()
    fun clear() { held.clear() }
    fun handle(code: Int, action: Int, repeat: Int, controls: Boolean, send: (VodRemoteCommand) -> Unit): Boolean {
        held[code]?.let { command ->
            if (action == KeyEvent.ACTION_UP) held.remove(code)
            else if (action == KeyEvent.ACTION_DOWN && repeat > 0 && command in seek) send(command)
            return true
        }
        val command = when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> VodRemoteCommand.TOGGLE
            KeyEvent.KEYCODE_MEDIA_PLAY -> VodRemoteCommand.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> VodRemoteCommand.PAUSE
            KeyEvent.KEYCODE_MEDIA_REWIND -> VodRemoteCommand.REWIND
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> VodRemoteCommand.FORWARD
            KeyEvent.KEYCODE_MEDIA_NEXT -> VodRemoteCommand.NEXT
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!controls) VodRemoteCommand.REWIND else null
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controls) VodRemoteCommand.FORWARD else null
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (!controls) VodRemoteCommand.SHOW else null
            else -> null
        } ?: return false
        if (action == KeyEvent.ACTION_DOWN) {
            held[code] = command
            if (repeat == 0 || command in seek) send(command)
        }
        return action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP
    }
    private companion object { val seek = setOf(VodRemoteCommand.REWIND,VodRemoteCommand.FORWARD) }
}