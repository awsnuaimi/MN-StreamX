package com.yourapp.iptv.ui.screen.home

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Own both halves of OK. OEM repeat events cannot turn a hold into a playback click. */
internal fun Modifier.liveTvRowInput(onClick: () -> Unit, onLongClick: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onLongClick)
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    var key by remember { mutableStateOf<Key?>(null) }
    var fired by remember { mutableStateOf(false) }
    var timer by remember { mutableStateOf<Job?>(null) }
    fun clear() { timer?.cancel(); timer = null; key = null; fired = false }
    DisposableEffect(Unit) { onDispose { timer?.cancel() } }
    onFocusChanged { if (!it.isFocused) clear() }.onPreviewKeyEvent { event ->
        val activate = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!activate) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (key == null && event.nativeKeyEvent.repeatCount == 0) {
                    key = event.key; fired = false
                    timer = scope.launch {
                        delay(timeout)
                        if (key != null) { fired = true; hold() }
                    }
                }
                true
            }
            KeyEventType.KeyUp -> {
                val play = key == event.key && !fired && !event.nativeKeyEvent.isCanceled
                clear()
                if (play) click()
                true
            }
            else -> false
        }
    }
}