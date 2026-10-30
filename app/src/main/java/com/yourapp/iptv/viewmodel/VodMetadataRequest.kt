package com.yourapp.iptv.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Main-thread request ownership, independent of a coroutine's final completion. */
internal class VodMetadataRequest(private val scope: CoroutineScope) {
    private var active: Any? = null
    private var job: Job? = null

    fun launch(block: suspend Completion.() -> Unit) {
        if (active != null || !scope.isActive) return
        val token = Any()
        active = token
        // Register the job before its body can publish state or accept a retry.
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try { Completion(token).block() }
            finally { release(token) }
        }
        job = next
        // A lazy child cancelled before start does not execute its finally block.
        next.invokeOnCompletion { release(token) }
        next.start()
    }

    fun cancel() {
        val previous = job
        active = null
        job = null
        previous?.cancel()
    }

    private fun release(token: Any): Boolean {
        if (active !== token) return false
        active = null
        job = null
        return true
    }

    internal inner class Completion internal constructor(private val token: Any) {
        fun complete(publish: () -> Unit) {
            // State observers may retry synchronously from inside publish().
            if (scope.isActive && release(token)) publish()
        }
    }
}