package mnstudio.apps.mnstreamx.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import mnstudio.apps.mnstreamx.data.backup.editAppData
import mnstudio.apps.mnstreamx.domain.model.LiveTvPreferences
import mnstudio.apps.mnstreamx.domain.model.AppSettings
import mnstudio.apps.mnstreamx.domain.model.PlaylistCacheKey
import mnstudio.apps.mnstreamx.domain.profile.ProfileScope
import mnstudio.apps.mnstreamx.domain.repository.LiveTvPreferencesRepository
import mnstudio.apps.mnstreamx.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.io.IOException

/** Separate personal keys share the existing organization store and its backup transaction. */
internal class LiveTvPreferencesDataStore(private val store: DataStore<Preferences>,
    private val committedSettings: suspend () -> AppSettings, private val profiles: ProfileRepository) : LiveTvPreferencesRepository {
    override fun observe(scope: String): Flow<LiveTvPreferences> {
        require(ProfileScope.valid(scope))
        return store.data.map { LiveTvPreferencesCodec.decode(it[key(scope)]) }
            .distinctUntilChanged().flowOn(Dispatchers.Default)
    }
    override suspend fun update(scope: String, change: (LiveTvPreferences) -> LiveTvPreferences) {
        require(ProfileScope.valid(scope))
        store.editAppData { prefs ->
            val people = profiles.state.value
            if (!people.ready || people.failed || people.switching ||
                people.catalog.activeId != ProfileScope.profile(scope)) throw IOException("Profile changed")
            val account = committedSettings()
            if (!account.hasSelectedSource() || PlaylistCacheKey.of(account) != ProfileScope.source(scope)) throw IOException("Source changed")
            val current = LiveTvPreferencesCodec.decode(prefs[key(scope)])
            val next = change(current).validate()
            if (next != current) prefs[key(scope)] = LiveTvPreferencesCodec.encode(next)
        }
    }
    private fun key(scope: String) = stringPreferencesKey("live_$scope")
}