package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.data.backup.editAppData

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.repository.StartupPlaybackRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val Context.startupPlaybackStore by preferencesDataStore(name = "startup_playback",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

class StartupPlaybackDataStore(private val store: DataStore<Preferences>) : StartupPlaybackRepository {
    override fun observe() = store.data.map { values ->
        values.asMap()[enabledKey] as? Boolean ?: false
    }.distinctUntilChanged()

    override suspend fun save(enabled: Boolean) { store.editAppData { it[enabledKey] = enabled } }

    private companion object {
        val enabledKey = booleanPreferencesKey("resume_last_channel")
    }
}