package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.profile.ProfileScope
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.backup.editAppData
import com.yourapp.iptv.domain.diagnostics.*

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException

internal val Context.watchHistoryStore by preferencesDataStore(name = "watch_history",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

class WatchHistoryDataStore(private val store: DataStore<Preferences>, private val settings: SettingsRepository,
    scope: CoroutineScope, private val profiles: ProfileRepository = ProfileRepository.Disabled) : WatchHistoryRepository {
    private val writer = WatchHistoryWriter(scope,::apply)
    private class OwnerChanged : IOException("History owner changed")

    override fun observe(sourceKey: String): Flow<List<WatchHistoryEntry>> {
        require(validKey(sourceKey))
        return store.data.map { WatchHistoryCodec.decode(it[key(sourceKey)]) }
            .distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override fun recordStarted(sourceKey: String, entry: WatchHistoryEntry) {
        if (!validKey(sourceKey) || entry.channelId.isBlank() || entry.channelId.length > 512 || entry.watchedAtMillis < 0) return
        if (!writer.record(sourceKey,entry)) report(sourceKey,DiagnosticIssue.HISTORY_QUEUE)
    }

    override suspend fun remove(sourceKey: String, channelId: String) {
        require(channelId.isNotBlank() && channelId.length <= 512)
        submit(sourceKey,channelId)
    }

    override suspend fun clear(sourceKey: String) = submit(sourceKey,null)

    private suspend fun submit(source: String, remove: String?) {
        require(validKey(source))
        writer.edit(source,remove)
    }
    private suspend fun apply(command: HistoryWrite) {
        when (command) {
            is HistoryWrite.Edit -> update(command.source) { rows ->
                if (command.remove == null) emptyList() else rows.filterNot { it.channelId == command.remove }
            }
            is HistoryWrite.Records -> for ((source,records) in command.batch.entries()) {
                try { update(source,records::apply) }
                catch (error: CancellationException) { throw error }
                catch (_: OwnerChanged) { /* Stale source or retired profile: intentionally discarded. */ }
                catch (_: Exception) { report(source,DiagnosticIssue.HISTORY_WRITE) }
            }
        }
    }
    private suspend fun update(sourceKey: String, change: (List<WatchHistoryEntry>) -> List<WatchHistoryEntry>) {
        val source = settings.observeSettings().first()
        if (!source.hasSelectedSource() || PlaylistCacheKey.of(source) != ProfileScope.source(sourceKey))
            throw OwnerChanged()
        store.editAppData { values ->
            if (!ProfileScope.belongs(sourceKey,profiles.state.value)) throw OwnerChanged()
            val key = key(sourceKey)
            val previous = WatchHistoryCodec.decode(values[key])
            val rows = change(previous)
            if (rows.isEmpty()) values.remove(key)
            else {
                val now = System.currentTimeMillis()
                val updated = maxOf(now, values.asMap().filter { it.key.name.startsWith("history_") }.values
                    .maxOfOrNull { WatchHistoryCodec.updated(it as? String ?: "") }?.plus(1) ?: now)
                if (rows != previous) values[key] = WatchHistoryCodec.encode(rows, updated)
            }
            values.asMap().filter { it.key.name.startsWith("history_") &&
                ProfileScope.profile(it.key.name.removePrefix("history_")) == ProfileScope.profile(sourceKey) }.entries
                .sortedByDescending { WatchHistoryCodec.updated(it.value as? String ?: "") }
                .drop(32).forEach { values.remove(stringPreferencesKey(it.key.name)) }
        }
    }

    private fun report(source: String, issue: DiagnosticIssue) =
        DiagnosticJournal.record(DiagnosticArea.SYSTEM,issue,ProfileScope.source(source))

    private fun validKey(value: String) = ProfileScope.valid(value)
    private fun key(source: String) = stringPreferencesKey("history_$source")
}