package com.yourapp.iptv.data.datastore

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

/** Stores only hashed playlist provenance and bounded VOD timestamps. No stream URLs or credentials. */
class VodProgressDataStore(
    private val store: DataStore<Preferences>,
    private val settings: SettingsRepository,
    scope: CoroutineScope
) : VodProgressRepository {
    private data class Command(val source: String, val entry: VodProgressEntry, val completed: Boolean)
    private val pending = Channel<Command>(Channel.BUFFERED)

    init {
        scope.launch {
            for (command in pending) {
                try { apply(command) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* A transient disk error must not crash video playback. */ }
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
        if (!source.hasSelectedSource() || PlaylistCacheKey.of(source) != command.source) return
        store.edit { preferences ->
            val itemKey = key(command.source)
            val previous = VodProgressCodec.decode(preferences[itemKey])
            val updated = VodProgress.record(previous, command.entry, command.completed)
            if (previous != updated) {
                if (updated.isEmpty()) preferences.remove(itemKey)
                else preferences[itemKey] = VodProgressCodec.encode(updated)
            }
        }
    }

    private fun validSource(value: String) = value.length == 64 &&
        value.all { it in '0'..'9' || it in 'a'..'f' }
    private fun key(source: String) = stringPreferencesKey("vod_$source")
}