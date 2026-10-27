package com.yourapp.iptv.data.recovery

import android.app.Application
import com.yourapp.iptv.domain.recovery.*
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class ResetInterruptionTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun processDeathAfterIntentBeforeEraseDoesNotExposeTheOldSubscriptionsOrDefaultProfile() = runTest {
        val privateRoot = temporary.newFolder();val marker = File(privateRoot,"no_backup/full_reset_v1.pending")
        val credentials = File(privateRoot,"files/datastore/settings").apply { parentFile!!.mkdirs();writeText("synthetic credentials") }
        val defaultHistory = File(privateRoot,"databases/history").apply { parentFile!!.mkdirs();writeText("default and p1 scopes") }
        DeviceAppResetJournal(marker).mark()
        val eraser = ResetEraser().apply { accepted = false }
        val restarted = StoredAppDataReset(DeviceAppResetJournal(marker),eraser,backgroundScope,StandardTestDispatcher(testScheduler))
        restarted.initialize()
        assertTrue(restarted.state.value.blocked);assertEquals(0,eraser.calls)
        assertTrue(credentials.exists());assertTrue(defaultHistory.exists())
        restarted.request();assertTrue(restarted.state.value.blocked)
        assertTrue(marker.exists());assertTrue(credentials.exists());assertTrue(defaultHistory.exists())
    }
    @Test fun androidEraseContractCoversAllPrivateScopesAndPreservesAnExportedBackup() = runTest {
        val privateRoot = temporary.newFolder();val exported = temporary.newFile().apply { writeText("exported backup") }
        val areas = listOf("files/datastore/settings","files/datastore/user_profiles","databases/favorites",
            "files/default-history","files/p1-history","cache/channels","cache/epg","no_backup/parental_v1.bin",
            "shared_prefs/caption_cloud_secrets.xml","files/caption-models/model")
        areas.forEach { name -> File(privateRoot,name).apply { parentFile!!.mkdirs();writeText("synthetic private data") } }
        val marker = File(privateRoot,"no_backup/full_reset_v1.pending")
        // This double models Android's whole-app erase; application code never deletes individual areas.
        val eraser = AppDataEraser { assertTrue(marker.exists());privateRoot.deleteRecursively() }
        val reset = StoredAppDataReset(DeviceAppResetJournal(marker),eraser,backgroundScope,StandardTestDispatcher(testScheduler))
        reset.initialize();reset.request()
        assertTrue(reset.state.value.blocked);assertFalse(privateRoot.exists());assertEquals("exported backup",exported.readText())
        val freshProcess = StoredAppDataReset(DeviceAppResetJournal(marker),ResetEraser(),backgroundScope,StandardTestDispatcher(testScheduler))
        freshProcess.initialize();assertFalse(freshProcess.state.value.blocked)
    }
}