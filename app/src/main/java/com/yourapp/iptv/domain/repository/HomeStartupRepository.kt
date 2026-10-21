package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.HomeStartupSnapshot
import com.yourapp.iptv.domain.organization.OrganizationConfig
import com.yourapp.iptv.domain.organization.OrganizedChannels

interface HomeStartupRepository {
    suspend fun restore(source: AppSettings): HomeStartupSnapshot?
    suspend fun save(source: AppSettings, config: OrganizationConfig, catalog: OrganizedChannels, refreshedAt: Long)

    companion object {
        val Disabled = object : HomeStartupRepository {
            override suspend fun restore(source: AppSettings): HomeStartupSnapshot? = null
            override suspend fun save(source: AppSettings, config: OrganizationConfig,
                catalog: OrganizedChannels, refreshedAt: Long) = Unit
        }
    }
}