package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.LiveTvPreferences
import kotlinx.coroutines.flow.Flow

interface LiveTvPreferencesRepository {
    fun observe(scope: String): Flow<LiveTvPreferences>
    /** Transform the latest durable value; never replace from an earlier screen snapshot. */
    suspend fun update(scope: String, change: (LiveTvPreferences) -> LiveTvPreferences)
}