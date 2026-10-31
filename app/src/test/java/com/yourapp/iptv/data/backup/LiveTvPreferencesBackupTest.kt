package com.yourapp.iptv.data.backup

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.LiveTvPreferencesCodec
import com.yourapp.iptv.data.local.dao.ProfileFavoriteDao
import com.yourapp.iptv.data.profile.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LiveTvPreferencesBackupTest {
    private val choice = LiveTvPreferences(LiveTvSort.NAME, setOf("one"), setOf("Group"), preferredQualities = mapOf("f".repeat(64) to "hd"),
        adaptive = AdaptiveLiveNavigation().record("a".repeat(64), "b".repeat(64)).record("a".repeat(64), "b".repeat(64)).withEnabled(false))
    @Test fun existingBackupsWithoutPersonalKeysRemainByteCompatible() {
        val old = BackupPayloadCodec.encode(backupPayload())
        assertArrayEquals(old, BackupPayloadCodec.encode(BackupPayloadCodec.decode(old)))
    }
    @Test fun personalKeysRoundTripForEachProfileButOrphanedAndUnknownSourceKeysAreExcluded() {
        val account = backupAccounts(); val source = PlaylistCacheKey.of(account.active.settings)
        val people = UserProfiles().entries + profileRow()
        val raw = "live_$source"; val personal = "live_${ProfileScope.key(PROFILE_A, source)}"
        val stores = BackupStore.entries.associateWith { emptyPreferences() }.toMutableMap()
        val text = LiveTvPreferencesCodec.encode(choice)
        stores[BackupStore.ORGANIZATION] = mutablePreferencesOf(stringPreferencesKey(raw) to text,
            stringPreferencesKey(personal) to text, stringPreferencesKey("live_${"f".repeat(64)}") to text,
            stringPreferencesKey("live_${ProfileScope.key(PROFILE_B, source)}") to text)
        val areas = PortablePreferences.collect(stores, account, people)
        assertEquals(setOf(raw, personal), areas.getValue(BackupStore.ORGANIZATION).keys)
        val payload = backupPayload().copy(areas = areas, profiles = people)
        val restored = BackupPayloadCodec.decode(BackupPayloadCodec.encode(payload))
        assertEquals(payload, restored)
        val bad = payload.copy(areas = areas + (BackupStore.ORGANIZATION to mapOf("live_bad" to BackupValue(BackupValueType.STRING, text))))
        assertTrue(runCatching { BackupPayloadCodec.encode(bad) }.isFailure)
    }
    @Test fun restoreUsesSettingsChoiceAndNeverFeedsPersonalRecordsIntoTheFolderCodec() {
        val payload = backupPayload(); val name = "live_" + PlaylistCacheKey.of(payload.subscriptions.active.settings)
        val text = LiveTvPreferencesCodec.encode(choice)
        val saved = payload.copy(areas = payload.areas + (BackupStore.ORGANIZATION to mapOf(name to BackupValue(BackupValueType.STRING, text))))
        val old = BackupStore.entries.associateWith { emptyPreferences() }
        val keep = BackupMerge.stores(old, saved, BackupRestoreOptions(settings = false, folders = true))
        assertNull(keep.getValue(BackupStore.ORGANIZATION)[stringPreferencesKey(name)])
        val apply = BackupMerge.stores(old, saved, BackupRestoreOptions(settings = true, folders = false))
        assertEquals(choice, LiveTvPreferencesCodec.decode(apply.getValue(BackupStore.ORGANIZATION)[stringPreferencesKey(name)]))
    }
    @Test fun retiringAProfilePurgesOnlyItsPersonalPreferencesAndKeepsSharedOrganization() = runBlocking {
        val source = "a".repeat(64); val a = "live_${ProfileScope.key(PROFILE_A, source)}"
        val b = "live_${ProfileScope.key(PROFILE_B, source)}"
        val disk = ProfileMemory(mutablePreferencesOf(stringPreferencesKey(a) to "A", stringPreferencesKey(b) to "B",
            stringPreferencesKey("source_$source") to "shared", stringPreferencesKey("live_$source") to "default"))
        val dao = object : ProfileFavoriteDao { override suspend fun removeNamespace(prefix: String) { assertEquals("p1:$PROFILE_A:", prefix) } }
        AppDataGate.write { ProfileCleaner(dao, listOf(disk)).purge(PROFILE_A) }
        assertNull(disk.values.value[stringPreferencesKey(a)])
        assertEquals("B", disk.values.value[stringPreferencesKey(b)])
        assertEquals("shared", disk.values.value[stringPreferencesKey("source_$source")])
        assertEquals("default", disk.values.value[stringPreferencesKey("live_$source")])
    }
}