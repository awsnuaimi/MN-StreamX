package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.data.backup.editAppData

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.IOException

internal val Context.watchHistoryStore by preferencesDataStore(name = "watch_history",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

class WatchHistoryDataStore(private val store: DataStore<Preferences>, private val settings: SettingsRepository,
    scope: CoroutineScope) : WatchHistoryRepository {
    private data class Command(val source: String, val entry: WatchHistoryEntry? = null,
        val remove: String? = null, val clear: Boolean = false, val reply: CompletableDeferred<Unit>? = null)
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init { scope.launch {
        for (command in commands) {
            try { apply(command); command.reply?.complete(Unit) }
            catch (error: CancellationException) { command.reply?.completeExceptionally(error); throw error }
            catch (error: Exception) { command.reply?.completeExceptionally(error) }
        }
    } }

    override fun observe(sourceKey: String): Flow<List<WatchHistoryEntry>> {
        require(validKey(sourceKey))
        return store.data.map { WatchHistoryCodec.decode(it[key(sourceKey)]) }
            .distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override fun recordStarted(sourceKey: String, entry: WatchHistoryEntry) {
        if (!validKey(sourceKey) || entry.channelId.isBlank() || entry.channelId.length > 512 || entry.watchedAtMillis < 0) return
        commands.trySend(Command(sourceKey, entry = entry))
    }

    override suspend fun remove(sourceKey: String, channelId: String) {
        require(channelId.isNotBlank() && channelId.length <= 512)
        submit(Command(sourceKey, remove = channelId))
    }

    override suspend fun clear(sourceKey: String) = submit(Command(sourceKey, clear = true))

    private suspend fun submit(command: Command) {
        require(validKey(command.source))
        val reply = CompletableDeferred<Unit>()
        commands.send(command.copy(reply = reply))
        reply.await()
    }

    private suspend fun apply(command: Command) {
        val source = settings.observeSettings().first()
        if (!source.hasSelectedSource() || PlaylistCacheKey.of(source) != command.source)
            throw IOException("History source changed")
        store.editAppData { values ->
            val key = key(command.source)
            val previous = WatchHistoryCodec.decode(values[key])
            val rows = when {
                command.clear -> emptyList()
                command.remove != null -> previous.filterNot { it.channelId == command.remove }
                command.entry != null -> WatchHistory.record(previous, command.entry)
                else -> previous
            }
            if (rows.isEmpty()) values.remove(key)
            else {
                val now = System.currentTimeMillis()
                val updated = maxOf(now, values.asMap().filter { it.key.name.startsWith("history_") }.values
                    .maxOfOrNull { WatchHistoryCodec.updated(it as? String ?: "") }?.plus(1) ?: now)
                if (rows != previous) values[key] = WatchHistoryCodec.encode(rows, updated)
            }
            values.asMap().filter { it.key.name.startsWith("history_") }.entries
                .sortedByDescending { WatchHistoryCodec.updated(it.value as? String ?: "") }
                .drop(32).forEach { values.remove(stringPreferencesKey(it.key.name)) }
        }
    }

    private fun validKey(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    private fun key(source: String) = stringPreferencesKey("history_$source")
}