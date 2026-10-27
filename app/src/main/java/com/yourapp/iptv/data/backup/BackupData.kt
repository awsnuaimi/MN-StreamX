package com.yourapp.iptv.data.backup

import androidx.datastore.preferences.core.Preferences
import com.yourapp.iptv.domain.model.Subscriptions

internal enum class BackupStore { SETTINGS, HOME, PLAYER, STARTUP, CAPTIONS, ORGANIZATION, HISTORY, VOD, SEARCH, PROFILES }
internal enum class BackupValueType { STRING, BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING_SET }
internal data class BackupValue(val type: BackupValueType, val text: String)
internal data class BackupFavorite(val source: String, val channel: String)
internal data class BackupPayload(val createdAt: Long, val subscriptions: Subscriptions,
    val areas: Map<BackupStore, Map<String, BackupValue>>, val favorites: List<BackupFavorite>,
    val profiles: List<com.yourapp.iptv.domain.profile.UserProfile> = com.yourapp.iptv.domain.profile.UserProfiles().entries) {
    override fun toString() = "BackupPayload(contents=redacted)"
}
internal data class BackupSnapshot(val stores: Map<BackupStore, Preferences>,
    val favorites: List<BackupFavorite>, val legacyFavorites: List<String>) {
    override fun toString() = "BackupSnapshot(contents=redacted)"
}
internal object BackupLimits {
    const val PLAIN = 16 * 1024 * 1024
    const val FILE = PLAIN + 64
    const val JOURNAL = 32 * 1024 * 1024
    const val FAVORITES = 10_000
}