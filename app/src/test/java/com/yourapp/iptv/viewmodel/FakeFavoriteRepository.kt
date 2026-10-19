package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.repository.FavoriteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

internal class FakeFavoriteRepository(initial: Set<String> = emptySet()) : FavoriteRepository {
    val ids = MutableStateFlow(initial)
    var failRead = false
    var failWrite = false
    var writes = 0
    override fun observeFavoriteIds() = flow {
        if (failRead) error("private-read-details")
        ids.collect { emit(it) }
    }
    override suspend fun toggleFavorite(channelId: String) {
        writes++
        delay(10)
        if (failWrite) error("private-write-details")
        ids.value = if (channelId in ids.value) ids.value - channelId else ids.value + channelId
    }
}