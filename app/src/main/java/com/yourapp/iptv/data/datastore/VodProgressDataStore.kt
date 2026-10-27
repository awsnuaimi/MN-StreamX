package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.profile.ProfileScope
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.backup.editAppData

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.PlaylistCacheKey
import com.yourapp.iptv.domain.model.VodProgress
import com.yourapp.iptv.domain.model.VodProgressEntry
import com.yourapp.iptv.domain.repository.SettingsRepository
import com.yourapp.iptv.domain.repository.VodProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal val Context.vodProgressStore by preferencesDataStore(
    name = "vod_progress", corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class VodProgressDataStore(
    private val store: DataStore<Preferences>,
    private val settings: SettingsRepository,
    scope: CoroutineScope, private val profiles: ProfileRepository = ProfileRepository.Disabled
) : VodProgressRepository {
    private data class Command(val source: String, val entry: VodProgressEntry, val completed: Boolean)
    private val pending = Channel<Command>(Channel.BUFFERED)

    init {
        scope.launch {
            for (command in pending) {
                try { apply(command) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { }
            }
        }
    }

    override fun observe(sourceKey: String): Flow<List<VodProgressEntry>> {
        require(validSource(sourceKey))
        return store.data.map { VodProgressCodec.decode(it[key(sourceKey)]) }.distinctUntilChanged()
    }

    override fun record(sourceKey: String, entry: VodProgressEntry, completed: Boolean) {
        if (!validSource(sourceKey) || !VodProgress.isValid(entry)) return
        pending.trySend(Command(sourceKey, entry, completed))
    }

    private suspend fun apply(command: Command) {
        val source = settings.observeSettings().first()
        if (!source.hasSelectedSource() || PlaylistCacheKey.of(source) != ProfileScope.source(command.source)) return
        store.editAppData { preferences ->
            check(ProfileScope.belongs(command.source,profiles.state.value))
            val itemKey = key(command.source)
            val previous = VodProgressCodec.decode(preferences[itemKey])
            val updated = VodProgress.record(previous, command.entry, command.completed)
            if (previous != updated) {
                if (updated.isEmpty()) preferences.remove(itemKey)
                else preferences[itemKey] = VodProgressCodec.encode(updated)
            }
        }
    }

    private fun validSource(value: String) = ProfileScope.valid(value)
    private fun key(source: String) = stringPreferencesKey("vod_$source")
}