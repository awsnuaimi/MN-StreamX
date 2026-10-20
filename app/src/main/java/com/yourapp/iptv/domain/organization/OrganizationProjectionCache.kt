package com.yourapp.iptv.domain.organization

import com.yourapp.iptv.domain.model.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One bounded entry shared by home, guide and player. Source identity prevents account mixing. */
internal class OrganizationProjectionCache {
    private data class Entry(val source: List<Channel>, val config: OrganizationConfig,
        val result: OrganizedChannels)
    private val lock = Mutex()
    private var entry: Entry? = null

    suspend fun project(source: List<Channel>, config: OrganizationConfig,
        checkpoint: () -> Unit): OrganizedChannels = lock.withLock {
        checkpoint()
        val previous = entry
        if (previous != null && previous.source === source && previous.config == config) previous.result
        else ChannelOrganizer.project(source, config, checkpoint).also {
            checkpoint()
            entry = Entry(source, config, it)
        }
    }

    companion object { val Shared = OrganizationProjectionCache() }
}