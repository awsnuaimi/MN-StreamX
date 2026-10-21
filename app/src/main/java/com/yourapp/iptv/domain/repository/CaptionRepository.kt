package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.CaptionSettings
import kotlinx.coroutines.flow.Flow

interface CaptionRepository {
    fun observe(): Flow<CaptionSettings>
    suspend fun save(value: CaptionSettings)
}