package com.yourapp.iptv.ui.screen.home

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
internal fun LiveTvNumberLifetime(entry: LiveTvNumberEntry, onPause: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val pause by rememberUpdatedState(onPause)
    LaunchedEffect(entry, entry.revision) {
        if (entry.active) {
            val ticket = entry.revision
            delay(LiveTvNumberEntry.TIMEOUT_MS)
            entry.expire(ticket)
        }
    }
    DisposableEffect(owner, entry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) { entry.clear(); pause() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}