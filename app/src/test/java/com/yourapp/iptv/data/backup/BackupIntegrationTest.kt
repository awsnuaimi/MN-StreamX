package com.yourapp.iptv.data.backup

import android.app.Application
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.*
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import com.yourapp.iptv.data.repository.FavoriteRepositoryImpl
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.organization.OrganizationNames
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun device(seed: Byte) = BackupTestDevice(RuntimeEnvironment.getApplication(), temporary.newFolder(), seed)

    @Test fun aBackupFromAnotherDeviceRestoresAllAreasWithoutItsOldKeystoreKey() = runBlocking {
        val source = device(1); val target = device(7)
        try {
            source.start(); source.seed(); target.start("C")
            val c = PlaylistCacheKey.of(backupAccount("C")); val a = PlaylistCacheKey.of(backupAccount("A"))
            target.database.scopedFavoriteDao().add(ScopedFavoriteEntity(c, "existing"))
            val bytes = source.repository.export(BackupTestDevice.password())
            assertFalse(String(bytes, Charsets.ISO_8859_1).contains("synthetic.invalid"))
            val before = target.snapshot()
            val preview = target.repository.inspect(bytes, BackupTestDevice.password())
            assertEquals(1, preview.newSubscriptions); assertEquals(1, preview.favoriteCount)
            assertEquals(before, target.snapshot())
            target.repository.restore(preview.token, BackupRestoreOptions())
            val accounts = target.settings.observeSubscriptions().first()
            assertEquals(setOf("A", "C"), accounts.entries.map { it.settings.xtreamUsername }.toSet())
            assertEquals("A", accounts.active.settings.xtreamUsername)
            val favorites = FavoriteRepositoryImpl(target.database.scopedFavoriteDao(), target.settings)
            assertEquals(setOf("42"), favorites.observeFavoriteIds(a).first())
            assertEquals(setOf("existing"), favorites.observeFavoriteIds(c).first())
            val home = HomeAppearanceDataStore(target.stores.getValue(BackupStore.HOME)).observe().first()
            assertEquals(HomeTheme.RED, home.theme); assertTrue(home.isNightMode); assertEquals(HomeLanguage.GERMAN, home.language)
            assertEquals(PlayerTextSize.LARGE, PlayerAppearanceDataStore(target.stores.getValue(BackupStore.PLAYER)).observe().first().textSize)
            assertTrue(StartupPlaybackDataStore(target.stores.getValue(BackupStore.STARTUP)).observe().first())
            assertEquals(CaptionLanguage.GERMAN, CaptionDataStore(target.stores.getValue(BackupStore.CAPTIONS)).observe().first().target)
            val history = WatchHistoryCodec.decode(target.stores.getValue(BackupStore.HISTORY).data.first()[stringPreferencesKey("history_$a")])
            assertEquals("42", history.single().channelId)
            val progress = VodProgressCodec.decode(target.stores.getValue(BackupStore.VOD).data.first()[stringPreferencesKey("vod_$a")])
            assertEquals(40_000L, progress.single().positionMs)
            assertTrue(SearchHistoryDataStore(target.stores.getValue(BackupStore.SEARCH)).observe(a).first().contains("Synthetic title"))
            val organizationKey = OrganizationNames.sourceKey(backupAccount("A"))
            val config = OrganizationCodec.decode(target.stores.getValue(BackupStore.ORGANIZATION).data.first()[stringPreferencesKey("source_$organizationKey")], organizationKey)
            assertEquals("Custom folder", config.folders.first { it.id == "arabic" }.name)
            assertEquals(4L, config.revision)
            assertFalse(target.journal.exists())
            assertEquals(BackupMaintenance.READY, target.repository.maintenance.value)
        } finally { source.close(); target.close() }
    }
    @Test fun importingTwiceDoesNotDuplicateSubscriptionsOrFavorites() = runBlocking {
        val source = device(2); val target = device(8)
        try {
            source.start(); source.seed(); target.start("C")
            val bytes = source.repository.export(BackupTestDevice.password())
            repeat(2) {
                val preview = target.repository.inspect(bytes, BackupTestDevice.password())
                target.repository.restore(preview.token, BackupRestoreOptions())
            }
            assertEquals(2, target.settings.observeSubscriptions().first().entries.size)
            assertEquals(1, target.database.backupDao().scoped().size)
        } finally { source.close(); target.close() }
    }
    @Test fun wrongPasswordAndModifiedFileLeaveAllCurrentDataUntouched() = runBlocking {
        val source = device(3); val target = device(9)
        try {
            source.start(); source.seed(); target.start("C")
            val bytes = source.repository.export(BackupTestDevice.password()); val before = target.snapshot()
            assertTrue(runCatching { target.repository.inspect(bytes, "Wrong-backup-password".toCharArray()) }.isFailure)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            assertTrue(runCatching { target.repository.inspect(bytes, BackupTestDevice.password()) }.isFailure)
            assertEquals(before, target.snapshot()); assertFalse(target.journal.exists())
        } finally { source.close(); target.close() }
    }
    @Test fun aFailureAfterPartialWritesRollsEveryStoreAndTheRoomFavoritesBack() = runBlocking {
        val source = device(4); val target = device(10)
        try {
            source.start(); source.seed(); target.start("C")
            var fail = true
            val faulty = object : BackupPersistence by target.storage {
                override suspend fun apply(snapshot: BackupSnapshot) {
                    if (fail) {
                        fail = false
                        target.stores.getValue(BackupStore.HOME).updateData { snapshot.stores.getValue(BackupStore.HOME) }
                        target.database.backupDao().replace(snapshot.favorites.map { ScopedFavoriteEntity(it.source, it.channel) }, emptyList())
                        throw IOException("Synthetic write failure")
                    }
                    target.storage.apply(snapshot)
                }
            }
            val repository = EncryptedBackupRepository(faulty, target.journal); repository.recover()
            val before = target.snapshot()
            val preview = repository.inspect(source.repository.export(BackupTestDevice.password()), BackupTestDevice.password())
            assertEquals(BackupProblem.STORAGE, (runCatching { repository.restore(preview.token, BackupRestoreOptions()) }.exceptionOrNull() as BackupException).problem)
            assertEquals(before, target.snapshot()); assertFalse(target.journal.exists())
            assertEquals(BackupMaintenance.READY, repository.maintenance.value)
        } finally { source.close(); target.close() }
    }
    @Test fun aFailedRollbackKeepsAnEncryptedJournalAndANewRepositoryRecoversItBeforeUse() = runBlocking {
        val source = device(5); val target = device(11)
        try {
            source.start(); source.seed(); target.start("C")
            val before = target.snapshot()
            val broken = object : BackupPersistence by target.storage {
                override suspend fun apply(snapshot: BackupSnapshot) {
                    target.stores.getValue(BackupStore.HOME).updateData { snapshot.stores.getValue(BackupStore.HOME) }
                    throw IOException("Synthetic persistent failure")
                }
            }
            val repository = EncryptedBackupRepository(broken, target.journal); repository.recover()
            val preview = repository.inspect(source.repository.export(BackupTestDevice.password()), BackupTestDevice.password())
            assertTrue(runCatching { repository.restore(preview.token, BackupRestoreOptions()) }.isFailure)
            assertEquals(BackupMaintenance.RECOVERY_REQUIRED, repository.maintenance.value); assertTrue(target.journal.exists())
            val restarted = EncryptedBackupRepository(target.storage, target.journal)
            assertEquals(BackupMaintenance.STARTING, restarted.maintenance.value)
            restarted.recover()
            assertEquals(before, target.snapshot()); assertFalse(target.journal.exists())
            assertEquals(BackupMaintenance.READY, restarted.maintenance.value)
        } finally { source.close(); target.close() }
    }
    @Test fun processDeathAfterWritingTheJournalIsReplayedFromTheActualDiskFile() = runBlocking {
        val source = device(6); val target = device(12)
        try {
            source.start(); source.seed(); target.start("C")
            val before = target.snapshot()
            val incoming = source.storage.payload(source.snapshot())
            AppDataGate.write {
                target.journal.save(before)
                target.storage.apply(target.storage.restored(before, incoming, BackupRestoreOptions()))
            }
            assertNotEquals(before, target.snapshot())
            val restarted = EncryptedBackupRepository(target.storage, target.journal)
            restarted.recover()
            assertEquals(before, target.snapshot())
        } finally { source.close(); target.close() }
    }
    @Test fun normalDataStoreWritersWaitUntilTheRestoreHasCommitted() = runBlocking {
        val source = device(13); val target = device(14)
        val release = CompletableDeferred<Unit>()
        try {
            source.start(); source.seed(); target.start("C")
            val entered = CompletableDeferred<Unit>()
            val delayed = object : BackupPersistence by target.storage {
                override suspend fun apply(snapshot: BackupSnapshot) {
                    entered.complete(Unit); release.await(); target.storage.apply(snapshot)
                }
            }
            val repository = EncryptedBackupRepository(delayed, target.journal); repository.recover()
            val preview = repository.inspect(source.repository.export(BackupTestDevice.password()), BackupTestDevice.password())
            val restoring = async(Dispatchers.IO) { repository.restore(preview.token, BackupRestoreOptions()) }
            entered.await()
            val attempted = CompletableDeferred<Unit>()
            val normal = async(Dispatchers.IO) {
                attempted.complete(Unit)
                HomeAppearanceDataStore(target.stores.getValue(BackupStore.HOME)).save(HomeAppearance(theme = HomeTheme.LIGHT))
            }
            attempted.await(); assertFalse(normal.isCompleted)
            release.complete(Unit); restoring.await(); normal.await()
            assertEquals(HomeTheme.LIGHT, HomeAppearanceDataStore(target.stores.getValue(BackupStore.HOME)).observe().first().theme)
        } finally { release.complete(Unit); source.close(); target.close() }
    }
    @Test fun theRecoveryJournalPreservesUnknownTypedPreferencesAndRejectsADifferentDeviceKey() = runBlocking {
        val device = device(15)
        try {
            device.start(); device.seed()
            device.stores.getValue(BackupStore.HOME).edit {
                it[floatPreferencesKey("future_float")] = 1.25f
                it[stringSetPreferencesKey("future_set")] = setOf("one", "two")
            }
            val before = device.snapshot()
            device.journal.save(before)
            assertEquals(before, device.journal.read())
            val file = temporary.root.walkTopDown().first { it.name == "restore.journal" }
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("Synthetic title"))
            val wrong = DeviceBackupJournal(file) { javax.crypto.spec.SecretKeySpec(ByteArray(32) { 99 }, "AES") }
            assertTrue(runCatching { wrong.read() }.isFailure); assertTrue(device.journal.exists())
            device.journal.clear()
        } finally { device.close() }
    }
}