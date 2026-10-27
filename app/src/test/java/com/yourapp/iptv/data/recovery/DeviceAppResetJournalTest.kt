package com.yourapp.iptv.data.recovery

import android.app.Application
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class DeviceAppResetJournalTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun durableMarkerSurvivesReopeningWithoutTouchingOtherPrivateData() {
        val file = File(temporary.root,"pending")
        val unrelated = File(temporary.root,"subscriptions").apply { writeText("preserve-until-Android-erases") }
        val journal = DeviceAppResetJournal(file);assertFalse(journal.pending());journal.mark()
        assertTrue(DeviceAppResetJournal(file).pending());assertTrue(file.readBytes().isNotEmpty())
        assertEquals("preserve-until-Android-erases",unrelated.readText())
    }
    @Test fun emptyCorruptBackupAndInterruptedNewMarkersAllKeepStartupBlocked() {
        for (suffix in listOf("",".bak",".new")) {
            val file = File(temporary.newFolder(),"pending")
            File(file.path + suffix).writeBytes(byteArrayOf())
            assertTrue(DeviceAppResetJournal(file).pending())
        }
    }
    @Test fun aFailedWriteDoesNotReportACompletedIntent() {
        val parent = temporary.newFile("not-a-directory")
        val journal = DeviceAppResetJournal(File(parent,"pending"))
        assertTrue(runCatching { journal.mark() }.isFailure);assertFalse(journal.pending())
    }
    @Test fun markerContainsNoSubscriptionsProfilesPinOrRecoverySecret() {
        val file = File(temporary.root,"pending");DeviceAppResetJournal(file).mark()
        assertEquals("MN-StreamX/full-reset/v1",file.readText())
    }
}