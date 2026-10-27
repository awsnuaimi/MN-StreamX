package com.yourapp.iptv.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yourapp.iptv.data.backup.AppDataGate
import com.yourapp.iptv.domain.parental.ParentalSection
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.security.*
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class ProfilePublicationRaceTest {
    @Test fun anOldSuccessfulReadCannotReplaceANewerDirectlyPublishedCommit() = runBlocking {
        staleReadCannotReplaceCommit(null)
    }
    @Test fun anOldFailedReadCannotLockANewerVerifiedCommit() = runBlocking {
        staleReadCannotReplaceCommit(IOException("Synthetic stale read failure"))
    }
    private suspend fun staleReadCannotReplaceCommit(failure: Exception?) = coroutineScope {
        val disk = ProfileMemory()
        ProfilePublicationFixture(disk).use { f ->
            f.start();val pause = f.pauseNextRead(failure)
            disk.values.value = f.encoded(UserProfiles(revision = 1))
            pause.awaitEntry()
            val writing = async(Dispatchers.Default) { f.profiles.rename("default","Latest") }
            try {
                assertEquals(ProfileResult.OK,withTimeout(3_000) { writing.await() })
                val committed = f.profiles.state.value.catalog
                assertEquals(2L,committed.revision);assertEquals("Latest",committed.active.name)
                pause.release();f.drain()
                assertEquals(committed,f.profiles.state.value.catalog);assertFalse(f.profiles.state.value.failed)
            } finally { pause.release();writing.join() }
        }
    }
    @Test fun backupRollbackCanPublishALowerRevisionRatherThanBeingMistakenForAStaleRead() = runBlocking {
        val disk = ProfileMemory()
        ProfilePublicationFixture(disk).use { f ->
            f.start();val before = f.profiles.state.value.catalog
            val changed = UserProfiles(entries = before.entries + profileRow(),activeId = PROFILE_A,revision = 40)
            AppDataGate.write { disk.values.value = f.encoded(changed) }
            f.awaitCatalog(changed);val epoch = f.profiles.state.value.epoch
            AppDataGate.write { disk.values.value = f.encoded(before) }
            f.awaitCatalog(before)
            assertTrue(f.profiles.state.value.epoch > epoch);assertFalse(f.profiles.state.value.failed)
        }
    }
    @Test fun anIntermediateRestoreRecordIsDiscardedAfterTheTransactionRollsBack() = runBlocking {
        val disk = ProfileMemory()
        ProfilePublicationFixture(disk).use { f ->
            f.start();f.profiles.rename("default","Before");val before = f.profiles.state.value.catalog
            val pause = f.pauseNextRead(SecureStorageException(SecureStorageFailure.AUTHENTICATION_FAILED))
            try {
                AppDataGate.write {
                    disk.values.value = f.encoded(UserProfiles(revision = 50))
                    pause.awaitEntry();disk.values.value = f.encoded(before)
                }
                pause.release();f.drain()
                assertFalse(f.profiles.state.value.failed);assertEquals(before,f.profiles.state.value.catalog)
            } finally { pause.release() }
        }
    }
    @Test fun keyLossAfterDurableWriteFailsClosedUntilAnExplicitVerifiedRetry() = runBlocking {
        val memory = ProfileMemory();var afterWrite: () -> Unit = {}
        val disk = object : DataStore<Preferences> by memory {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                memory.updateData(transform).also { afterWrite() }
        }
        ProfilePublicationFixture(disk).use { f ->
            f.start();val before = f.profiles.state.value.catalog
            afterWrite = { f.decryptFailure = SecureStorageException(SecureStorageFailure.KEY_MISSING) }
            assertEquals(ProfileResult.STORAGE,f.profiles.rename("default","Committed"))
            assertEquals(before,f.profiles.state.value.catalog)
            assertEquals(SecureStorageFailure.KEY_MISSING,f.profiles.state.value.storageFailure)
            assertFalse(f.access.state.value.allows(ParentalSection.LIVE))
            assertEquals("Committed",f.catalog().active.name)
            afterWrite = {};f.decryptFailure = null;f.access.initialize()
            assertFalse(f.profiles.state.value.failed);assertEquals("Committed",f.profiles.state.value.catalog.active.name)
        }
    }
    @Test fun anEncryptedResultThatDoesNotMatchTheRequestedCatalogCannotReturnSuccess() = runBlocking {
        ProfilePublicationFixture().use { f ->
            f.start();val before = f.profiles.state.value.catalog
            f.encryptInstead = ProfileCodec.encode(before)
            assertEquals(ProfileResult.STORAGE,f.profiles.rename("default","Not in the encrypted result"))
            assertEquals(before,f.profiles.state.value.catalog);assertTrue(f.profiles.state.value.failed)
            assertFalse(f.access.state.value.allows(ParentalSection.LIVE))
        }
    }
    @Test fun verificationFailureIsPublishedBeforeTheSwitchingMaskIsRemoved() = runBlocking {
        val memory = ProfileMemory();var afterWrite: () -> Unit = {}
        val disk = object : DataStore<Preferences> by memory {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                memory.updateData(transform).also { afterWrite() }
        }
        ProfilePublicationFixture(disk).use { f ->
            f.start(true);f.profiles.create("Adult",ProfileKind.ADULT)
            val target = f.profiles.state.value.catalog.entries.last().id
            val events = CopyOnWriteArrayList<ProfilesState>()
            // This collector only captures synchronous notifications; production work still
            // uses independent observer/IO schedulers and real transaction locks.
            val collector = launch(Dispatchers.Unconfined) { f.profiles.state.collect { events.add(it) } }
            try {
                afterWrite = { f.decryptFailure = SecureStorageException(SecureStorageFailure.KEY_MISSING) }
                assertEquals(ProfileResult.STORAGE,f.profiles.select(target))
                val switching = events.indexOfFirst { it.switching };assertTrue(switching >= 0)
                assertFalse(events.drop(switching).any { it.ready && !it.switching && !it.failed })
                assertTrue(f.profiles.state.value.failed);assertFalse(f.profiles.state.value.switching)
                assertEquals("default",f.profiles.state.value.catalog.activeId)
            } finally { collector.cancelAndJoin() }
        }
    }
}