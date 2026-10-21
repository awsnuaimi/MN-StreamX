package com.yourapp.iptv.domain.model

import com.yourapp.iptv.domain.organization.OrganizationConfig
import com.yourapp.iptv.domain.organization.OrganizedChannels

data class HomeStartupSnapshot(val config: OrganizationConfig, val catalog: OrganizedChannels,
    val refreshedAtMillis: Long)

object HomeStartupPolicy {
    const val FRESH_FOR_MS = 15 * 60 * 1000L
    fun needsRefresh(refreshedAt: Long, now: Long): Boolean =
        refreshedAt <= 0 || now < refreshedAt || now - refreshedAt >= FRESH_FOR_MS
}