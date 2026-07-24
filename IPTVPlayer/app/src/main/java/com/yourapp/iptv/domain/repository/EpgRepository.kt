package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.Program

interface EpgRepository {
    suspend fun getPrograms(channelId: String): List<Program>
}