package mnstudio.apps.mnstreamx.data.datastore

import mnstudio.apps.mnstreamx.domain.profile.ProfileScope
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import mnstudio.apps.mnstreamx.data.backup.editAppData

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import mnstudio.apps.mnstreamx.domain.model.PlaylistCacheKey
import mnstudio.apps.mnstreamx.domain.model.VodProgress
import mnstudio.apps.mnstreamx.domain.model.VodProgressEntry
import mnstudio.apps.mnstreamx.domain.model.VodCompletedEpisode
import mnstudio.apps.mnstreamx.domain.model.VodEpisodeHistory
import mnstudio.apps.mnstreamx.domain.repository.SettingsRepository
import mnstudio.apps.mnstreamx.domain.repository.VodProgressRepository
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

    override fun observeCompleted(sourceKey: String): Flow<List<VodCompletedEpisode>> {
        require(validSource(sourceKey))
        return store.data.map { VodProgressCodec.decodeState(it[key(sourceKey)]).completed }.distinctUntilChanged()
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
            val previous = VodProgressCodec.decodeState(preferences[itemKey])
            val updated = VodEpisodeHistory.record(previous, command.entry, command.completed)
            if (previous != updated) {
                if (updated.entries.isEmpty() && updated.completed.isEmpty()) preferences.remove(itemKey)
                else preferences[itemKey] = VodProgressCodec.encodeState(updated)
            }
        }
    }

    private fun validSource(value: String) = ProfileScope.valid(value)
    private fun key(source: String) = stringPreferencesKey("vod_$source")
}