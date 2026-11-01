package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.VodProgressEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.yourapp.iptv.domain.model.VodCompletedEpisode

interface VodProgressRepository {
    fun observe(sourceKey: String): Flow<List<VodProgressEntry>>
    fun observeCompleted(sourceKey: String): Flow<List<VodCompletedEpisode>> = flowOf(emptyList())
    /** In-order, non-blocking enqueue. Never blocks Media3 or the Compose main thread. */
    fun record(sourceKey: String, entry: VodProgressEntry, completed: Boolean = false)
}