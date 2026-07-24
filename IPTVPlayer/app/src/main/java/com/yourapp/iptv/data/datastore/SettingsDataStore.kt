package com.yourapp.iptv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val M3U_URL_KEY = stringPreferencesKey("m3u_url")
        private val XTREAM_SERVER_KEY = stringPreferencesKey("xtream_server")
        private val XTREAM_USERNAME_KEY = stringPreferencesKey("xtream_username")
        private val XTREAM_PASSWORD_KEY = stringPreferencesKey("xtream_password")
        private val EPG_URL_KEY = stringPreferencesKey("epg_url")
    }

    // حفظ رابط M3U
    suspend fun saveM3uUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[M3U_URL_KEY] = url
        }
    }

    // استرجاع رابط M3U
    fun getM3uUrl(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[M3U_URL_KEY]
        }
    }

    // حفظ بيانات Xtream
    suspend fun saveXtreamData(server: String, username: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[XTREAM_SERVER_KEY] = server
            preferences[XTREAM_USERNAME_KEY] = username
            preferences[XTREAM_PASSWORD_KEY] = password
        }
    }

    // استرجاع بيانات Xtream
    fun getXtreamData(): Flow<Triple<String?, String?, String?>> {
        return context.dataStore.data.map { preferences ->
            Triple(
                preferences[XTREAM_SERVER_KEY],
                preferences[XTREAM_USERNAME_KEY],
                preferences[XTREAM_PASSWORD_KEY]
            )
        }
    }

    // حفظ رابط EPG
    suspend fun saveEpgUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[EPG_URL_KEY] = url
        }
    }

    // استرجاع رابط EPG
    fun getEpgUrl(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[EPG_URL_KEY]
        }
    }

    // مسح جميع الإعدادات
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}