package com.yourapp.iptv.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.AppSettings
import com.yourapp.iptv.domain.model.PlaylistSource
import com.yourapp.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsDataStore

    private companion object {
        val M3U = stringPreferencesKey("m3u_url")
        val SERVER = stringPreferencesKey("xtream_server")
        val USERNAME = stringPreferencesKey("xtream_username")
        val PASSWORD = stringPreferencesKey("xtream_password")
        val EPG = stringPreferencesKey("epg_url")
        val SOURCE = stringPreferencesKey("playlist_source")
    }

    override fun observeSettings(): Flow<AppSettings> = store.data.map { values ->
        AppSettings(
            m3uUrl = values[M3U].orEmpty(),
            xtreamServer = values[SERVER].orEmpty(),
            xtreamUsername = values[USERNAME].orEmpty(),
            xtreamPassword = values[PASSWORD].orEmpty(),
            epgUrl = values[EPG].orEmpty(),
            source = values[SOURCE]?.let { saved ->
                PlaylistSource.entries.firstOrNull { it.name == saved }
            } ?: if (values[M3U].isNullOrBlank() && !values[SERVER].isNullOrBlank()) {
                PlaylistSource.XTREAM
            } else PlaylistSource.M3U
        )
    }

    override suspend fun saveSettings(settings: AppSettings) {
        store.edit { values ->
            values[M3U] = settings.m3uUrl
            values[SERVER] = settings.xtreamServer
            values[USERNAME] = settings.xtreamUsername
            values[PASSWORD] = settings.xtreamPassword
            values[EPG] = settings.epgUrl
            values[SOURCE] = settings.source.name
        }
    }

    fun getM3uUrl(): Flow<String?> = observeSettings().map { it.m3uUrl }
    fun getEpgUrl(): Flow<String?> = observeSettings().map { it.epgUrl }
    fun getXtreamData(): Flow<Triple<String?, String?, String?>> = observeSettings().map {
        Triple(it.xtreamServer, it.xtreamUsername, it.xtreamPassword)
    }

    suspend fun saveM3uUrl(url: String) {
        store.edit { it[M3U] = url }
    }

    suspend fun saveEpgUrl(url: String) {
        store.edit { it[EPG] = url }
    }

    suspend fun saveXtreamData(server: String, username: String, password: String) {
        store.edit {
            it[SERVER] = server
            it[USERNAME] = username
            it[PASSWORD] = password
        }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}