package com.yourapp.iptv.ui.screen.player

/** Consume the complete OK press so its release cannot activate a newly focused action. */
internal class PlayerDeckActivation {
    private var held: Int? = null
    fun reset() { held = null }
    fun handle(code: Int, action: Int, repeat: Int, canOpen: Boolean, onOpen: () -> Unit): Boolean {
        if (held == code) {
            if (action == 1) held = null
            return true
        }
        if (code !in setOf(23, 66, 160) || !canOpen || action != 0) return false
        held = code
        if (repeat == 0) onOpen()
        return true
    }
}