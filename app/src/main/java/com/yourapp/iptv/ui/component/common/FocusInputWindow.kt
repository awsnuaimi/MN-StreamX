package com.yourapp.iptv.ui.component.common

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window

/** Public Window.Callback contract; all events and other callbacks retain their original owner. */
internal class FocusInputWindow private constructor(private val previous: Window.Callback) : Window.Callback by previous {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        FocusInputEpoch.key(event)
        return previous.dispatchKeyEvent(event)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        FocusInputEpoch.touch(event)
        return previous.dispatchTouchEvent(event)
    }
    fun close(window: Window) {
        if (window.callback === this) window.callback = previous
    }

    companion object {
        fun install(window: Window): FocusInputWindow? {
            val previous = window.callback ?: return null
            if (previous is FocusInputWindow) return previous
            return FocusInputWindow(previous).also { window.callback = it }
        }
    }
}