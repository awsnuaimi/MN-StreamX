package com.yourapp.iptv.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.data.local.AppDatabase
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import com.yourapp.iptv.data.subscription.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.*
import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

internal fun backupAccount(user: String) = AppSettings(source = PlaylistSource.XTREAM,
    xtreamServer = "https://synthetic.invalid", xtreamUsername = user, xtreamPassword = " synthetic & password ")
internal fun backupAccounts(user: String = "A") = Subscriptions(listOf(Subscription("default", user, backupAccount(user))), "default", null)
internal fun backupPayload(user: String = "A"): BackupPayload {
    val stores = BackupStore.entries.associateWith { emptyPreferences() }
    val accounts = backupAccounts(user)
    return BackupPayload(1000, accounts, PortablePreferences.collect(stores, accounts), emptyList())
}

internal class BackupTestDevice(context: Context, directory: File, seed: Byte) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val stores: Map<BackupStore, DataStore<Preferences>> = BackupStore.entries.associateWith { area ->
        PreferenceDataStoreFactory.create(scope = scope) { File(directory, "${area.name}.preferences_pb") }
    }
    val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val cipher = KeystoreSubscriptionCipher { SecretKeySpec(ByteArray(32) { seed }, "AES") }
    val settings = SettingsDataStore(stores.getValue(BackupStore.SETTINGS), cipher)
    val storage = BackupStorage(stores, database.backupDao(), database.scopedFavoriteDao(), settings, cipher)
    val journal = DeviceBackupJournal(File(directory, "restore.journal")) { SecretKeySpec(ByteArray(32) { (seed + 1).toByte() }, "AES") }
    val repository = EncryptedBackupRepository(storage, journal)
    suspend fun start(user: String = "A") {
        settings.saveSubscription("default", user, backupAccount(user))
        repository.recover()
    }
    suspend fun seed() {
        val account = backupAccount("A"); val key = PlaylistCacheKey.of(account)
        database.scopedFavoriteDao().add(ScopedFavoriteEntity(key, "42"))
        stores.getValue(BackupStore.HOME).edit { HomeAppearanceCodec.encode(it, HomeAppearance(
            language = HomeLanguage.GERMAN, theme = HomeTheme.RED, isNightMode = true)) }
        stores.getValue(BackupStore.PLAYER).edit { PlayerAppearanceCodec.encode(it, PlayerAppearance(textSize = PlayerTextSize.LARGE)) }
        stores.getValue(BackupStore.STARTUP).edit { it[booleanPreferencesKey("resume_last_channel")] = true }
        stores.getValue(BackupStore.CAPTIONS).edit { CaptionCodec.encode(it, CaptionSettings(target = CaptionLanguage.GERMAN)) }
        stores.getValue(BackupStore.HISTORY).edit { it[stringPreferencesKey("history_$key")] =
            WatchHistoryCodec.encode(listOf(WatchHistoryEntry("42", 100)), 100) }
        stores.getValue(BackupStore.VOD).edit { it[stringPreferencesKey("vod_$key")] = VodProgressCodec.encode(listOf(
            VodProgressEntry(VodKind.MOVIE, "42", "Synthetic title", "mp4", 40_000, 200_000, 100))) }
        stores.getValue(BackupStore.SEARCH).edit { it[stringPreferencesKey("search_$key")] =
            """{"version":1,"updated":100,"queries":["Synthetic title"]}""" }
        val organizationKey = OrganizationNames.sourceKey(account)
        val config = OrganizationConfig(organizationKey, enabled = true,
            folders = OrganizationDefaults.folders().map { if (it.id == "arabic") it.copy(name = "Custom folder") else it }, revision = 3)
        stores.getValue(BackupStore.ORGANIZATION).edit { it[stringPreferencesKey("source_$organizationKey")] = OrganizationCodec.encode(config) }
    }
    suspend fun snapshot() = AppDataGate.write { storage.capture() }
    suspend fun close() { scope.coroutineContext[Job]!!.cancelAndJoin(); database.close() }
    companion object { fun password() = "Synthetic-backup-password-42".toCharArray() }
}