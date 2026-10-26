package com.yourapp.iptv.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.yourapp.iptv.data.subscription.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.repository.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** One preferences transaction owns the encrypted accounts and active account together. */
class SettingsDataStore internal constructor(
    private val store: DataStore<Preferences>, private val cipher: SubscriptionCipher
) : SubscriptionRepository {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore, KeystoreSubscriptionCipher())
    private val accounts = stringPreferencesKey("subscriptions_encrypted_v1")

    override fun observeSubscriptions(): Flow<Subscriptions> = flow {
        // Complete legacy encryption before exposing settings to any consumer or profile switch.
        store.edit { if (it[accounts] == null) write(it, LegacySettingsCodec.read(it)) }
        emitAll(store.data.map(::read).distinctUntilChanged())
    }.flowOn(Dispatchers.IO)

    override fun observeSettings(): Flow<AppSettings> = observeSubscriptions().map { it.active.settings }.distinctUntilChanged()

    override suspend fun saveSettings(settings: AppSettings) { modify { old ->
        SubscriptionChanges.save(old, old.activeId, old.active.name, settings)
    } }
    override suspend fun saveSubscription(id: String?, name: String, settings: AppSettings) = modify {
        SubscriptionChanges.save(it, id, name, settings)
    }
    override suspend fun activateSubscription(id: String) = modify { SubscriptionChanges.activate(it, id) }
    override suspend fun removeSubscription(id: String) = modify { SubscriptionChanges.remove(it, id) }

    private suspend fun modify(change: (Subscriptions) -> Subscriptions): Subscriptions = withContext(Dispatchers.IO) {
        var committed: Subscriptions? = null
        store.edit { values ->
            val old = if (values[accounts] == null) LegacySettingsCodec.read(values) else read(values)
            val next = change(old)
            if (next != old || values[accounts] == null) write(values, next)
            committed = next
        }
        requireNotNull(committed)
    }
    private fun read(values: Preferences): Subscriptions = try {
        SubscriptionCodec.decode(cipher.decrypt(requireNotNull(values[accounts])))
    } catch (_: Exception) { throw IOException("Cannot read saved subscriptions") }
    private fun write(values: MutablePreferences, value: Subscriptions) {
        values[accounts] = cipher.encrypt(SubscriptionCodec.encode(value))
        LegacySettingsCodec.remove(values)
    }

    fun getM3uUrl(): Flow<String?> = observeSettings().map { it.m3uUrl }
    fun getEpgUrl(): Flow<String?> = observeSettings().map { it.epgUrl }
    fun getXtreamData(): Flow<Triple<String?, String?, String?>> = observeSettings().map {
        Triple(it.xtreamServer, it.xtreamUsername, it.xtreamPassword)
    }
    suspend fun saveM3uUrl(url: String) = modify { old -> old.copy(entries = old.entries.map {
        if (it.id == old.activeId) it.copy(settings = it.settings.copy(m3uUrl = url)) else it
    }) }
    suspend fun saveEpgUrl(url: String) = modify { old -> old.copy(entries = old.entries.map {
        if (it.id == old.activeId) it.copy(settings = it.settings.copy(epgUrl = url)) else it
    }) }
    suspend fun saveXtreamData(server: String, username: String, password: String) = modify { old -> old.copy(entries = old.entries.map {
        if (it.id == old.activeId) it.copy(settings = it.settings.copy(xtreamServer = server, xtreamUsername = username, xtreamPassword = password)) else it
    }) }
    suspend fun clearAll() { store.edit { it.clear() } }
}