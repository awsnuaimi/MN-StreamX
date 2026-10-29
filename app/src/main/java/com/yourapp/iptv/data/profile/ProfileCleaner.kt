package com.yourapp.iptv.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.local.dao.ProfileFavoriteDao
import com.yourapp.iptv.domain.profile.UserProfiles

/** Called under AppDataGate after the catalog retires an inactive UUID; queued writers reject retired IDs. */
internal class ProfileCleaner(private val favorites: ProfileFavoriteDao,
    private val stores: List<DataStore<Preferences>>) {
    suspend fun purge(id: String) {
        require(UserProfiles.validId(id) && id != UserProfiles.DEFAULT_ID)
        val prefix = "p1:$id:"
        favorites.removeNamespace(prefix)
        for (store in stores) store.updateData { prefs -> prefs.toMutablePreferences().apply {
            asMap().keys.filter { key ->
                listOf("history_","vod_","search_","session_","live_").any { key.name.startsWith(it + prefix) }
            }.forEach { key -> remove(stringPreferencesKey(key.name)) }
        } }
    }
}