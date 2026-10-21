package com.yourapp.iptv.domain.repository

import com.yourapp.iptv.domain.model.HomeBrowseSession

interface HomeSessionRepository {
    suspend fun read(sourceKey: String): HomeBrowseSession?
    /** Queue the latest position. The implementation batches writes outside the UI lifetime. */
    fun remember(sourceKey: String, value: HomeBrowseSession)
    /** Request an immediate background commit when leaving the screen or stopping the activity. */
    fun flush()

    companion object {
        val Disabled = object : HomeSessionRepository {
            override suspend fun read(sourceKey: String): HomeBrowseSession? = null
            override fun remember(sourceKey: String, value: HomeBrowseSession) = Unit
            override fun flush() = Unit
        }
    }
}