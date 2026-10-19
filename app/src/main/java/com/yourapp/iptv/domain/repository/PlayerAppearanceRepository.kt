package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.PlayerAppearance
import kotlinx.coroutines.flow.Flow

interface PlayerAppearanceRepository {
    fun observe(): Flow<PlayerAppearance>
    suspend fun save(appearance: PlayerAppearance)
}