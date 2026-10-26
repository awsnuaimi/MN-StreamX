package com.yourapp.iptv.data.subscription

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.*

internal object LegacySettingsCodec {
    private val m3u = stringPreferencesKey("m3u_url")
    private val server = stringPreferencesKey("xtream_server")
    private val user = stringPreferencesKey("xtream_username")
    private val password = stringPreferencesKey("xtream_password")
    private val epg = stringPreferencesKey("epg_url")
    private val source = stringPreferencesKey("playlist_source")
    fun read(values: Preferences): Subscriptions {
        val s = AppSettings(m3uUrl = values[m3u].orEmpty(), xtreamServer = values[server].orEmpty(),
            xtreamUsername = values[user].orEmpty(), xtreamPassword = values[password].orEmpty(),
            epgUrl = values[epg].orEmpty(), source = values[source]?.let { name ->
                PlaylistSource.entries.firstOrNull { it.name == name }
            } ?: if (values[m3u].isNullOrBlank() && !values[server].isNullOrBlank()) PlaylistSource.XTREAM else PlaylistSource.M3U)
        return Subscriptions(listOf(Subscription(Subscriptions.DEFAULT_ID, "", s)), Subscriptions.DEFAULT_ID,
            s.takeIf { it.hasSelectedSource() }?.let(PlaylistCacheKey::of))
    }
    fun remove(values: MutablePreferences) {
        listOf(m3u, server, user, password, epg, source).forEach { values.remove(it) }
    }
}