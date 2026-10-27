package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.data.backup.editAppData

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.repository.StartupPlaybackRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.*
import com.yourapp.iptv.domain.repository.ProfileRepository

internal val Context.startupPlaybackStore by preferencesDataStore(name = "startup_playback",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

class StartupPlaybackDataStore(private val store: DataStore<Preferences>, private val profiles: ProfileRepository = ProfileRepository.Disabled) : StartupPlaybackRepository {
    override val profileId get() = profiles.state.value.catalog.activeId
    override fun observe() = combine(store.data,profiles.state) { values, people -> values to people }
        .filter { (_,people) -> people.ready && !people.failed && !people.switching }.map { (values,people) ->
            if (people.catalog.activeId == "default") values.asMap()[enabledKey] as? Boolean ?: false
            else people.catalog.active.resumeAtStartup
        }.distinctUntilChanged()

    override suspend fun save(enabled: Boolean) = saveFor(profileId,enabled)
    override suspend fun saveFor(profile: String, enabled: Boolean) {
        if (profile == "default") store.editAppData { it[enabledKey] = enabled }
        else profiles.saveStartup(profile,enabled)
    }

    private companion object {
        val enabledKey = booleanPreferencesKey("resume_last_channel")
    }
}