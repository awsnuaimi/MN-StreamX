package com.yourapp.iptv.data.backup

import android.app.Application
import androidx.datastore.preferences.core.*
import com.yourapp.iptv.data.datastore.LiveTvPreferencesCodec
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LiveTvPreferencesBackupIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun encryptedTransferBetweenDevicesRetainsPersonalChoicesAndLeavesNoRecoveryPending() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val first = BackupTestDevice(context, temporary.newFolder("first"), 1)
        val second = BackupTestDevice(context, temporary.newFolder("second"), 4)
        try {
            first.start(); second.start()
            val source = PlaylistCacheKey.of(backupAccount("A"))
            val key = stringPreferencesKey("live_$source")
            val choice = LiveTvPreferences(LiveTvSort.FAVORITES, setOf("opaque-channel"), setOf("Opaque group"), hiddenFolders = setOf("hidden-folder"), preferredQualities = mapOf("f".repeat(64) to "hd"),
                adaptive = AdaptiveLiveNavigation().record("a".repeat(64), "b".repeat(64)).record("a".repeat(64), "b".repeat(64)).withEnabled(false))
            first.stores.getValue(BackupStore.ORGANIZATION).edit { it[key] = LiveTvPreferencesCodec.encode(choice) }
            val exported = first.repository.export(BackupTestDevice.password())
            assertFalse(String(exported, Charsets.ISO_8859_1).contains("opaque-channel"))
            val preview = second.repository.inspect(exported, BackupTestDevice.password())
            assertEquals(0, preview.folderCount)
            second.repository.restore(preview.token, BackupRestoreOptions())
            val after = second.snapshot()
            assertEquals(choice, LiveTvPreferencesCodec.decode(after.stores.getValue(BackupStore.ORGANIZATION)[key]))
            assertEquals(BackupMaintenance.READY, second.repository.maintenance.value)
            second.repository.recover()
            assertEquals(choice, LiveTvPreferencesCodec.decode(second.snapshot().stores.getValue(BackupStore.ORGANIZATION)[key]))
        } finally { first.close(); second.close() }
    }
}