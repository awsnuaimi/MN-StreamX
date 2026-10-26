package com.yourapp.iptv.domain.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    fun observe(sourceKey: String): Flow<List<String>>
    suspend fun record(sourceKey: String, query: String)
    suspend fun clear(sourceKey: String)
}