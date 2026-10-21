package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.HomeBrowseSession
import com.yourapp.iptv.domain.repository.HomeSessionRepository
import kotlinx.coroutines.*

/** Restore once per source, unless the user has already taken control of navigation. */
internal class HomeSessionController(private val scope: CoroutineScope,
    private val repository: HomeSessionRepository, private val onLoaded: (HomeBrowseSession?) -> Unit) {
    private var source: String? = null
    private var enabled: Boolean? = null
    private var started = false
    private var touched = false
    private var epoch = 0L
    private var readJob: Job? = null

    fun open(key: String?) {
        repository.flush()
        readJob?.cancel(); epoch++
        source = key; started = false; touched = false
        start()
    }

    fun configure(value: Boolean) {
        enabled = value
        if (!value) {
            readJob?.cancel(); epoch++
            started = true; onLoaded(null)
        } else start()
    }

    fun interact() { touched = true }

    fun remember(value: HomeBrowseSession) {
        touched = true
        val key = source ?: return
        if (enabled == true) repository.remember(key, value)
    }

    fun flush() = repository.flush()

    private fun start() {
        if (started || enabled == null) return
        started = true
        val key = source
        if (enabled != true || key == null) { onLoaded(null); return }
        val token = epoch
        readJob = scope.launch {
            val saved = try { repository.read(key) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (token == epoch) onLoaded(saved.takeIf { !touched && enabled == true })
        }
    }
}