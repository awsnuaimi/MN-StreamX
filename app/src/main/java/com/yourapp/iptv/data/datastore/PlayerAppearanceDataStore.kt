package com.yourapp.iptv.data.datastore

import com.yourapp.iptv.data.backup.editAppData

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.repository.PlayerAppearanceRepository
import kotlinx.coroutines.flow.*

internal val Context.playerAppearanceStore by preferencesDataStore(name = "player_appearance")

class PlayerAppearanceDataStore(private val store: DataStore<Preferences>,
    private val profiles: com.yourapp.iptv.domain.repository.ProfileRepository = com.yourapp.iptv.domain.repository.ProfileRepository.Disabled) : PlayerAppearanceRepository {
    override fun observe() = kotlinx.coroutines.flow.combine(store.data,profiles.state) { prefs, people -> prefs to people }
        .filter { (_,people) -> people.ready && !people.failed && !people.switching }.map { (prefs,people) ->
            val row = people.catalog.active
            (if (row.id == "default") PlayerAppearanceCodec.decode(prefs) else requireNotNull(row.player))
                .copy(profileId = row.id)
        }.distinctUntilChanged()
    override suspend fun save(appearance: PlayerAppearance) {
        if (appearance.profileId != "default") profiles.savePlayer(appearance.profileId,appearance)
        else store.editAppData { check(com.yourapp.iptv.domain.profile.ProfileScope.belongs(
            "0".repeat(64),profiles.state.value));PlayerAppearanceCodec.encode(it,appearance) }
    }
}