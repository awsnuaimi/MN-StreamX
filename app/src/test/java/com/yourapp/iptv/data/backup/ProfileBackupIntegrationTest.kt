package com.yourapp.iptv.data.backup

import android.app.Application
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.profile.*
import com.yourapp.iptv.data.local.entity.ScopedFavoriteEntity
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.model.*
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ProfileBackupIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun cipher(seed: Int) = DeviceProfileCipher { SecretKeySpec(ByteArray(32) { (seed + 2).toByte() },"AES") }
    private fun device(seed: Int) = BackupTestDevice(RuntimeEnvironment.getApplication(),temporary.newFolder(),seed.toByte())
    private suspend fun seedProfiles(device: BackupTestDevice, seed: Int) {
        val people = UserProfiles(UserProfiles().entries + profileRow(kind = ProfileKind.CHILD),PROFILE_A)
        device.stores.getValue(BackupStore.PROFILES).edit { it[PROFILE_RECORD] = cipher(seed).encrypt(ProfileCodec.encode(people)) }
        val key = ProfileScope.key(PROFILE_A,PlaylistCacheKey.of(backupAccount("A")))
        device.database.scopedFavoriteDao().add(ScopedFavoriteEntity(key,"child-favorite"))
    }
    @Test fun portableProfileBackupUsesTheNewDevicesKeyAndPreservesDefaultSelection() = runBlocking {
        val source = device(1);val target = device(9)
        try {
            source.start();seedProfiles(source,1);target.start("C")
            val bytes = source.repository.export(BackupTestDevice.password())
            val preview = target.repository.inspect(bytes,BackupTestDevice.password());assertEquals(2,preview.profileCount)
            target.repository.restore(preview.token,BackupRestoreOptions())
            val encoded = target.stores.getValue(BackupStore.PROFILES).data.first()[PROFILE_RECORD]!!
            assertTrue(runCatching { cipher(1).decrypt(encoded) }.isFailure)
            val people = ProfileCodec.decode(cipher(9).decrypt(encoded));assertEquals("default",people.activeId)
            assertEquals(ParentalSection.entries.toSet(),people.entries.last().rules.sections)
            val key = ProfileScope.key(PROFILE_A,PlaylistCacheKey.of(backupAccount("A")))
            assertEquals(listOf("child-favorite"),target.database.scopedFavoriteDao().observeIds(key).first())
        } finally { source.close();target.close() }
    }
    @Test fun aPartialRestoreRollsTheEncryptedProfileCatalogAndFavoritesBackTogether() = runBlocking {
        val source = device(2);val target = device(10)
        try {
            source.start();seedProfiles(source,2);target.start("C");val before = target.snapshot();var fail = true
            val faulty = object : BackupPersistence by target.storage {
                override suspend fun apply(snapshot: BackupSnapshot) {
                    if (fail) { fail = false;target.stores.getValue(BackupStore.PROFILES).updateData { snapshot.stores.getValue(BackupStore.PROFILES) };throw IOException() }
                    target.storage.apply(snapshot)
                }
            }
            val repository = EncryptedBackupRepository(faulty,target.journal);repository.recover()
            val preview = repository.inspect(source.repository.export(BackupTestDevice.password()),BackupTestDevice.password())
            assertTrue(runCatching { repository.restore(preview.token,BackupRestoreOptions()) }.isFailure)
            assertEquals(before,target.snapshot());assertFalse(target.journal.exists())
        } finally { source.close();target.close() }
    }
}