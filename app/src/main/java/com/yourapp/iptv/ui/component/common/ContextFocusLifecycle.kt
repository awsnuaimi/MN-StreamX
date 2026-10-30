package com.yourapp.iptv.ui.component.common

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Returning from another destination restores a place; ordinary recomposition never does. */
@Composable
internal fun ContextFocus.Resume(contexts: Set<String>, fallback: String, preferred: String? = null,
    initial: Boolean = true) {
    val owner = LocalLifecycleOwner.current
    val currentContexts by rememberUpdatedState(contexts)
    val currentFallback by rememberUpdatedState(fallback)
    val currentPreferred by rememberUpdatedState(preferred)
    LaunchedEffect(this) {
        if (initial) restore(currentContexts, currentFallback, currentPreferred)
    }
    DisposableEffect(this, owner) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> { stopped = true; cancel() }
                Lifecycle.Event.ON_RESUME -> if (stopped) {
                    stopped = false
                    restore(currentContexts, currentFallback, currentPreferred)
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); cancel() }
    }
}