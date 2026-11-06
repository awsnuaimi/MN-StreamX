package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.organization.OrganizationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface OrganizationRepository {
    fun observe(): Flow<OrganizationConfig>
    suspend fun save(value: OrganizationConfig)
    companion object {
        val Disabled = object : OrganizationRepository {
            override fun observe() = flowOf(OrganizationConfig())
            override suspend fun save(value: OrganizationConfig) = Unit
        }
    }
}