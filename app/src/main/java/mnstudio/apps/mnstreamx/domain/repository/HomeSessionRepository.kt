package mnstudio.apps.mnstreamx.domain.repository

import mnstudio.apps.mnstreamx.domain.model.HomeBrowseSession

interface HomeSessionRepository {
    suspend fun read(sourceKey: String): HomeBrowseSession?
    fun remember(sourceKey: String, value: HomeBrowseSession)
    fun flush()

    companion object {
        val Disabled = object : HomeSessionRepository {
            override suspend fun read(sourceKey: String): HomeBrowseSession? = null
            override fun remember(sourceKey: String, value: HomeBrowseSession) = Unit
            override fun flush() = Unit
        }
    }
}