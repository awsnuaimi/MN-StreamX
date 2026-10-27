package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.domain.profile.ProfileScope
import com.yourapp.iptv.domain.repository.ProfileRepository
import com.yourapp.iptv.data.backup.editAppData

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.HomeBrowseSession
import com.yourapp.iptv.domain.repository.HomeSessionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal val Context.homeSessionStore by preferencesDataStore(name = "home_browse_session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

/** Application-owned writer: navigation or ViewModel disposal cannot cancel a queued commit. */
class HomeSessionDataStore(private val store: DataStore<Preferences>, scope: CoroutineScope, private val profiles: ProfileRepository = ProfileRepository.Disabled) : HomeSessionRepository {
    private data class Signal(val revision: Long = 0, val immediate: Boolean = false)
    private val signal = MutableStateFlow(Signal())
    private val guard = Any()
    private val pending = linkedMapOf<String, HomeBrowseSession>()
    private val latest = linkedMapOf<String, HomeBrowseSession>()

    init {
        scope.launch {
            signal.collectLatest { request ->
                if (request.revision == 0L) return@collectLatest
                if (!request.immediate) delay(350)
                // A tiny DataStore transaction finishes once begun; newer edits stay in pending.
                withContext(NonCancellable) { commit() }
            }
        }
    }

    override suspend fun read(sourceKey: String): HomeBrowseSession? {
        if (!validKey(sourceKey) || !ProfileScope.belongs(sourceKey,profiles.state.value)) return null
        synchronized(guard) { latest[sourceKey] }?.let { return it }
        return try { store.data.first()[key(sourceKey)]?.let(HomeSessionCodec::decode) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
    }

    override fun remember(sourceKey: String, value: HomeBrowseSession) {
        if (!validKey(sourceKey) || !ProfileScope.belongs(sourceKey,profiles.state.value)) return
        if ((value.group?.length ?: 0) > 4096 || listOf(value.rootFolderId, value.focusedChannelId,
                value.position.anchorId).any { (it?.length ?: 0) > 512 }) return
        synchronized(guard) {
            if (latest[sourceKey] == value) return
            latest.remove(sourceKey); latest[sourceKey] = value
            while (latest.size > 256) latest.remove(latest.keys.first())
            pending[sourceKey] = value
            while (pending.size > 256) pending.remove(pending.keys.first())
            signal.value = Signal(signal.value.revision + 1)
        }
    }

    override fun flush() = synchronized(guard) {
        if (pending.isNotEmpty()) signal.value = Signal(signal.value.revision + 1, true)
    }

    private suspend fun commit() {
        val rows = synchronized(guard) { pending.toMap() }
        if (rows.isEmpty()) return
        try {
            store.editAppData { values ->
                val now = System.currentTimeMillis()
                val last = values.asMap().filter { it.key.name.startsWith("session_") }.values
                    .maxOfOrNull { HomeSessionCodec.updated(it as? String ?: "").coerceAtMost(Long.MAX_VALUE - 33) } ?: 0L
                var updated = maxOf(now, last + 1)
                rows.filterKeys { ProfileScope.belongs(it,profiles.state.value) }.forEach { (source, session) ->
                    values[key(source)] = HomeSessionCodec.encode(session, updated++) }
                val saved = values.asMap().filter { it.key.name.startsWith("session_") }
                saved.entries.groupBy { ProfileScope.profile(it.key.name.removePrefix("session_")) }.values.forEach { bucket ->
                    bucket.sortedByDescending { HomeSessionCodec.updated(it.value as? String ?: "") }
                        .drop(32).forEach { values.remove(stringPreferencesKey(it.key.name)) }
                }
            }
            synchronized(guard) { rows.forEach { (source, session) -> if (pending[source] == session) pending.remove(source) } }
        } catch (_: Exception) { /* Optional navigation memory never blocks channels or playback. */ }
    }

    private fun validKey(value: String) = ProfileScope.valid(value)
    private fun key(value: String) = stringPreferencesKey("session_$value")
}