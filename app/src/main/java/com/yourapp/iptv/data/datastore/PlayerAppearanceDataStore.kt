package com.yourapp.iptv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.domain.model.PlayerAppearance
import com.yourapp.iptv.domain.repository.PlayerAppearanceRepository
import kotlinx.coroutines.flow.map

internal val Context.playerAppearanceStore by preferencesDataStore(name = "player_appearance")

class PlayerAppearanceDataStore(private val store: DataStore<Preferences>) : PlayerAppearanceRepository {
    override fun observe() = store.data.map(PlayerAppearanceCodec::decode)
    override suspend fun save(appearance: PlayerAppearance) {
        store.edit { PlayerAppearanceCodec.encode(it, appearance) }
    }
}