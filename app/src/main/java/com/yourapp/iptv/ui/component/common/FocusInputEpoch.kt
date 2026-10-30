package com.yourapp.iptv.ui.component.common

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/** Activity input still invalidates delayed focus when every child is temporarily disabled. */
internal object FocusInputEpoch {
    var value by mutableLongStateOf(0L)
        private set
    fun advance() { value++ }
    fun key(event: KeyEvent) { if (event.action == KeyEvent.ACTION_DOWN) advance() }
    fun touch(event: MotionEvent) { if (event.actionMasked == MotionEvent.ACTION_DOWN) advance() }
}