package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.Channel
import com.yourapp.iptv.domain.organization.*
import com.yourapp.iptv.domain.repository.OrganizationRepository
import com.yourapp.iptv.util.LoadTimings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.retryWhen

internal class OrganizationController(
    private val scope: CoroutineScope,
    repository: OrganizationRepository,
    private val background: CoroutineDispatcher,
    private val onChanged: (OrganizedChannels) -> Unit
) {
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
                rebuild()
                delay(5000)
                true
            }.collect {
                config = it
                readFailed = false
                rebuild()
            }
        }
    }

    fun clearChannels() {
        channels = null
        job?.cancel();job = null
        cachedSource = null;cachedConfig = null;cachedResult = null
        signaledFailure = null
    }

    fun setChannels(value: List<Channel>) {
        channels = value
        rebuild(force = true)
    }

    fun currentConfig(): OrganizationConfig? = config

    /** Adopt a validated disk projection without classifying or sorting it again. */
    fun restore(expected: OrganizationConfig, result: OrganizedChannels): Boolean {
        val current = config
        if (current != null && current.sourceKey == expected.sourceKey && current != expected) return false
        job?.cancel()
        config = expected
        channels = result.channels
        cachedSource = result.channels
        cachedConfig = expected
        cachedResult = result
        publish(result)
        return true
    }

    private fun publish(result: OrganizedChannels) {
        signaledFailure = readFailed

        onChanged(
            if (!readFailed) result else result.copy(
                tree = OrganizationTree(
                    result.tree.enabled,
                    result.tree.folders,
                    result.tree.counts,
                    true,
                    result.tree.folderSort
                )
            )
        )
    }

    private fun rebuild(force: Boolean = false) {
        val source = channels ?: return
        val settings = config ?: return

        job?.cancel()

        val cached = cachedResult
        if (source === cachedSource && settings == cachedConfig && cached != null) {
            if (force || signaledFailure != readFailed) publish(cached)
            return
        }

        job = scope.launch {
            val result = LoadTimings.measure(LoadTimings.Stage.ORGANIZE) {
                withContext(background) {
                    val worker = currentCoroutineContext()
                    OrganizationProjectionCache.Shared.project(source, settings) {
                        worker.ensureActive()
                    }
                }
            }

            cachedSource = source
            cachedConfig = settings
            cachedResult = result
            publish(result)
        }
    }
}