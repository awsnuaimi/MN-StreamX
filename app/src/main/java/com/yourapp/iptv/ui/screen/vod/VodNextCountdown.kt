package com.yourapp.iptv.ui.screen.vod

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/** A timer belongs to one ended episode and is cancelled on lifecycle loss or user interaction. */
@Composable
internal fun vodNextCountdown(ended: Boolean, hasNext: Boolean, enabled: Boolean,
    active: Boolean, cancelled: Boolean, onNext: () -> Unit): Int? {
    var seconds by remember { mutableStateOf<Int?>(null) }
    var fired by remember { mutableStateOf(false) }
    val next by rememberUpdatedState(onNext)
    LaunchedEffect(ended,hasNext,enabled,active,cancelled) {
        seconds = null
        if (!ended || !hasNext || !enabled || !active || cancelled || fired) return@LaunchedEffect
        seconds = 10
        repeat(10) { index -> delay(1000); seconds = 9 - index }
        fired = true
        seconds = null
        next()
    }
    return seconds
}