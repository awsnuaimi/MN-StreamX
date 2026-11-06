package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.AppSettings
import mnstudio.apps.mnstreamx.domain.model.HomeStartupSnapshot
import mnstudio.apps.mnstreamx.domain.organization.OrganizationConfig
import mnstudio.apps.mnstreamx.domain.organization.OrganizedChannels

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