package com.yourapp.iptv.domain.repository

import kotlinx.coroutines.flow.Flow

/** A device preference, separate from source credentials and playlist import settings. */
interface StartupPlaybackRepository {
    fun observe(): Flow<Boolean>
    suspend fun save(enabled: Boolean)
}