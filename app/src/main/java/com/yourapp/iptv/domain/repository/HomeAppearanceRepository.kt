package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.HomeAppearance
import kotlinx.coroutines.flow.Flow

interface HomeAppearanceRepository {
    fun observe(): Flow<HomeAppearance>
    suspend fun save(value: HomeAppearance)
}