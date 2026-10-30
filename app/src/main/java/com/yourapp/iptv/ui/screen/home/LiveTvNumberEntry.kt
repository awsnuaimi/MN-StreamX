package com.yourapp.iptv.ui.screen.home

import androidx.compose.runtime.*

/** Transient entry only: no saved state, catalog, credential, player, timer or unbounded key queue. */
internal class LiveTvNumberEntry {
    var digits by mutableStateOf(""); private set
    var revision by mutableLongStateOf(0); private set
    private var pressedCode: Int? = null
    private var pressedAt = 0L
    private var pressedRevision = -1L
    private var held = false
    val active get() = digits.isNotEmpty()
    val number get() = digits.toIntOrNull()
    val awaitingRelease get() = pressedCode != null
    fun digit(value: Int) {
        require(value in 0..9)
        if (digits.length >= MAX_DIGITS) return
        digits = if (digits == "0") "$value" else digits + value
        revision++
    }
    fun erase() { if (active) { digits = digits.dropLast(1); revision++ } }
    fun clear() {
        if (active || pressedCode != null) { digits = ""; revision++ }
        pressedCode = null; held = false
    }
    fun expire(ticket: Long) {
        if (ticket == revision) { digits = ""; revision++ } // Keep the consumed OK release owned until it arrives.
    }
    fun down(code: Int, time: Long, repeat: Boolean) {
        if (pressedCode == code) { held = held || repeat; return }
        if (active && !repeat && pressedCode == null) {
            pressedCode = code; pressedAt = time; pressedRevision = revision; held = false
        }
    }
    fun up(code: Int, time: Long, canceled: Boolean, longPressMillis: Long): Boolean {
        val result = active && pressedCode == code && pressedRevision == revision && !held && !canceled &&
            time >= pressedAt && time - pressedAt < longPressMillis
        pressedCode = null; held = false
        return result
    }
    companion object { const val MAX_DIGITS = 6; const val TIMEOUT_MS = 2_500L }
}