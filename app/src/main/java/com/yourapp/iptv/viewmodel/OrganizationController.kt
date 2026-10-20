package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.retryWhen

/** Reprojects only on source/config changes; search and favorites reuse the projected channels. */
internal class OrganizationController(private val scope: CoroutineScope, repository: OrganizationRepository,
    private val background: CoroutineDispatcher, private val onChanged: (OrganizedChannels) -> Unit) {
    private var config: OrganizationConfig? = null
    private var channels: List<Channel>? = null
    private var job: Job? = null
    private var readFailed = false
    private var cachedSource: List<Channel>? = null
    private var cachedConfig: OrganizationConfig? = null
    private var cachedResult: OrganizedChannels? = null
    private var signaledFailure: Boolean? = null
    init {
        scope.launch {
            repository.observe().retryWhen { error, _ ->
                if (error is CancellationException) throw error
                readFailed = true
                if (config == null) config = OrganizationConfig()
                rebuild(); delay(5000); true
            }.collect { config = it; readFailed = false; rebuild() }
        }
    }
    fun clearChannels() { channels = null; job?.cancel() }
    fun setChannels(value: List<Channel>) { channels = value; rebuild(force = true) }
    private fun publish(result: OrganizedChannels) {
        signaledFailure = readFailed
        onChanged(if (!readFailed) result else result.copy(tree = OrganizationTree(result.tree.enabled,
            result.tree.folders, result.tree.counts, true, result.tree.folderSort)))
    }
    private fun rebuild(force: Boolean = false) {
        val source = channels ?: return; val settings = config ?: return
        job?.cancel()
        val cached = cachedResult
        if (source === cachedSource && settings == cachedConfig && cached != null) {
            if (force || signaledFailure != readFailed) publish(cached)
            return
        }
        job = scope.launch {
            val result = withContext(background) { ChannelOrganizer.project(source, settings) }
            cachedSource = source; cachedConfig = settings; cachedResult = result
            publish(result)
        }
    }
}