package com.yourapp.iptv.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.data.local.dao.BackupDao
import com.yourapp.iptv.data.local.dao.ScopedFavoriteDao
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import com.yourapp.iptv.data.subscription.*
import com.yourapp.iptv.domain.model.Subscriptions
import com.yourapp.iptv.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first

internal class BackupStorage(
    private val stores: Map<BackupStore, DataStore<Preferences>>,
    private val favorites: BackupDao,
    private val scoped: ScopedFavoriteDao,
    private val subscriptions: SubscriptionRepository,
    private val cipher: SubscriptionCipher
) : BackupPersistence {
    constructor(context: Context, database: AppDatabase, subscriptions: SubscriptionRepository) : this(
        context.applicationContext.let { c -> mapOf(
            BackupStore.SETTINGS to c.settingsDataStore, BackupStore.HOME to c.homeAppearanceStore,
            BackupStore.PLAYER to c.playerAppearanceStore, BackupStore.STARTUP to c.startupPlaybackStore,
            BackupStore.CAPTIONS to c.captionStore, BackupStore.ORGANIZATION to c.organizationStore,
            BackupStore.HISTORY to c.watchHistoryStore, BackupStore.VOD to c.vodProgressStore,
            BackupStore.SEARCH to c.searchHistoryStore)
        }, database.backupDao(), database.scopedFavoriteDao(), subscriptions, KeystoreSubscriptionCipher())

    override suspend fun prepare() {
        val catalog = subscriptions.observeSubscriptions().first()
        catalog.legacySourceKey?.let { key -> AppDataGate.write { scoped.migrateLegacy(key) } }
    }
    override suspend fun capture(): BackupSnapshot {
        val values = stores.mapValues { it.value.data.first() }
        return BackupSnapshot(values, favorites.scoped().map { BackupFavorite(it.sourceKey, it.channelId) }, favorites.legacy())
    }
    override fun accounts(snapshot: BackupSnapshot): Subscriptions = SubscriptionCodec.decode(cipher.decrypt(
        requireNotNull(snapshot.stores[BackupStore.SETTINGS]?.get(ACCOUNTS))))
    override fun payload(snapshot: BackupSnapshot): BackupPayload {
        BackupSizeBudget.snapshot(snapshot, BackupLimits.PLAIN)
        val accounts = accounts(snapshot)
        val sources = PortablePreferences.sourceKeys(accounts)
        return BackupPayload(System.currentTimeMillis(), accounts, PortablePreferences.collect(snapshot.stores, accounts),
            snapshot.favorites.filter { it.source in sources })
    }
    override fun restored(before: BackupSnapshot, saved: BackupPayload, options: com.yourapp.iptv.domain.model.BackupRestoreOptions): BackupSnapshot {
        val accounts = BackupMerge.accounts(accounts(before), saved.subscriptions, options.activateSavedSubscription).value
        val values = BackupMerge.stores(before.stores, saved, options).toMutableMap()
        val settings = requireNotNull(before.stores[BackupStore.SETTINGS]).toMutablePreferences()
        settings[ACCOUNTS] = cipher.encrypt(SubscriptionCodec.encode(accounts))
        values[BackupStore.SETTINGS] = settings
        return BackupSnapshot(values, (before.favorites + saved.favorites).distinct(), before.legacyFavorites)
    }
    override suspend fun apply(snapshot: BackupSnapshot) {
        favorites.replace(snapshot.favorites.map { ScopedFavoriteEntity(it.source, it.channel) }, snapshot.legacyFavorites)
        for (area in BackupStore.entries.filterNot { it == BackupStore.SETTINGS } + BackupStore.SETTINGS) {
            val value = requireNotNull(snapshot.stores[area])
            requireNotNull(stores[area]).updateData { value }
        }
    }
    private companion object { val ACCOUNTS = stringPreferencesKey("subscriptions_encrypted_v1") }
}