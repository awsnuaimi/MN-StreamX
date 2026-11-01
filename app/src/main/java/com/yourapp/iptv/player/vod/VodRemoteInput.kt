package com.yourapp.iptv.player.vod

import android.view.KeyEvent
import com.yourapp.iptv.domain.model.VodSeek

enum class VodRemoteCommand { SHOW, REWIND, FORWARD, TOGGLE, PLAY, PAUSE, NEXT }

/** Own the whole press: showing controls must not turn its key-up into a button click. */
class VodRemoteInput {
    private val held = mutableMapOf<Int,VodRemoteCommand>()
    var seekStepMs: Long = VodSeek.STEP_MS
        private set
    fun clear() { held.clear(); seekStepMs = VodSeek.STEP_MS }
    private fun dispatchSeek(command: VodRemoteCommand, repeat: Int, send: (VodRemoteCommand) -> Unit) {
        if (VodSeek.shouldDispatchRepeat(repeat)) {
            seekStepMs = VodSeek.repeatStepMs(repeat)
            send(command)
        }
    }
    fun handle(code: Int, action: Int, repeat: Int, controls: Boolean, send: (VodRemoteCommand) -> Unit): Boolean {
        held[code]?.let { command ->
            if (action == KeyEvent.ACTION_UP) {
                held.remove(code)
                seekStepMs = VodSeek.STEP_MS
            } else if (action == KeyEvent.ACTION_DOWN && repeat > 0 && command in seek) {
                dispatchSeek(command, repeat, send)
            }
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
            if (command in seek) dispatchSeek(command, repeat, send)
            else if (repeat == 0) send(command)
        }
        return action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP
    }
    private companion object { val seek = setOf(VodRemoteCommand.REWIND,VodRemoteCommand.FORWARD) }
}