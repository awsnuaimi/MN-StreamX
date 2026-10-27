package com.yourapp.iptv.data.profile

import androidx.datastore.preferences.core.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class StoredProfilesTest {
    @Test fun existingDataRemainsInTheDefaultNamespaceAndNewProfileInheritsOnlyAppearance() = runBlocking {
        ProfileFixture().use { f -> f.start();assertEquals("default",f.repository.state.value.catalog.activeId)
            assertEquals(ProfileResult.OK,f.repository.create("Family",ProfileKind.ADULT))
            val added = f.last();assertEquals(HomeLanguage.GERMAN,added.home?.language)
            assertEquals(HomeTheme.RED,added.home?.theme);assertFalse(added.resumeAtStartup)
            assertNotEquals(ProfileScope.of(AppSettings()),ProfileScope.of(AppSettings(profileId = added.id)))
            assertFalse(f.disk.values.value[PROFILE_RECORD]!!.contains("Family"))
        }
    }
    @Test fun childRequiresPinStartsWithAllSectionsLockedAndConsumesTheAdultGrant() = runBlocking {
        ProfileFixture().use { f -> f.start()
            assertEquals(ProfileResult.PIN_SETUP_REQUIRED,f.repository.create("Child",ProfileKind.CHILD))
            f.pin.create("123456".toCharArray());assertEquals(ProfileResult.OK,f.repository.create("Child",ProfileKind.CHILD))
            val id = f.last().id;assertEquals(ProfileResult.OK,f.repository.select(id))
            assertFalse(f.access.state.value.unlocked);ParentalSection.entries.forEach { assertFalse(f.access.state.value.allows(it)) }
            assertEquals(ProfileResult.PIN_REQUIRED,f.repository.select("default"))
            assertEquals(ProfileResult.PIN_REQUIRED,f.repository.rename(id,"Changed"))
        }
    }
    @Test fun adultCannotBeOpenedFromAChildWithoutPinAndSuccessfulSwitchKeepsOnlyTheNewSessionGrant() = runBlocking {
        ProfileFixture().use { f -> f.start(true);f.repository.create("Child",ProfileKind.CHILD)
            f.repository.select(f.last().id);val before = f.access.state.value.epoch
            f.unlock();assertEquals(ProfileResult.OK,f.repository.select("default"))
            assertTrue(f.access.state.value.unlocked);assertTrue(f.access.state.value.epoch > before)
            f.access.background();assertFalse(f.access.state.value.allows(ParentalSection.LIVE))
            assertTrue(f.access.state.value.profileLocked)
        }
    }
    @Test fun lockedAdultCanChooseAMoreRestrictiveChildButCannotChooseAnotherAdult() = runBlocking {
        ProfileFixture().use { f -> f.start(true);f.repository.create("Adult",ProfileKind.ADULT);val adult = f.last().id
            f.repository.create("Child",ProfileKind.CHILD);val child = f.last().id;f.pin.lock()
            assertEquals(ProfileResult.PIN_REQUIRED,f.repository.select(adult))
            assertEquals(ProfileResult.OK,f.repository.select(child))
        }
    }
    @Test fun childrenCannotLoseSettingsProtectionOrDisableTheDevicePin() = runBlocking {
        ProfileFixture().use { f -> f.start(true);f.repository.create("Child",ProfileKind.CHILD);f.repository.select(f.last().id);f.unlock()
            assertEquals(ParentalResult.OK,f.access.saveRules(ParentalRules(emptySet())))
            assertEquals(setOf(ParentalSection.SETTINGS),f.access.state.value.rules.sections)
            assertFalse(f.access.state.value.allows(ParentalSection.SETTINGS));assertTrue(f.access.state.value.allows(ParentalSection.LIVE))
            val secret = "123456".toCharArray();assertEquals(ParentalResult.DENIED,f.access.changePin(secret,null))
            assertTrue(secret.all { it == '\u0000' });assertTrue(f.pin.state.value.configured)
        }
    }
    @Test fun disablingPinAndCreatingAChildCannotCommitAnUnprotectedChildInParallel() = runBlocking {
        ProfileFixture().use { f -> f.start(true)
            val reached = java.util.concurrent.CountDownLatch(1);val release = java.util.concurrent.CountDownLatch(1)
            f.pinDisk.beforeWrite = { reached.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS)) }
            val disabling = async(Dispatchers.Default) { f.access.changePin("123456".toCharArray(),null) }
            assertTrue(reached.await(5,java.util.concurrent.TimeUnit.SECONDS))
            val creating = async(Dispatchers.Default) { f.repository.create("Child",ProfileKind.CHILD) }
            release.countDown()
            assertEquals(ParentalResult.OK,disabling.await());assertEquals(ProfileResult.PIN_SETUP_REQUIRED,creating.await())
            assertFalse(f.pin.state.value.configured);assertEquals(1,f.repository.state.value.catalog.entries.size)
        }
    }
    @Test fun oldEditorWritesStayWithTheirOwnerAndADeletedOwnerCannotBeResurrected() = runBlocking {
        ProfileFixture().use { f -> f.start();f.repository.create("A",ProfileKind.ADULT);val a = f.last().id
            f.repository.create("B",ProfileKind.ADULT);val b = f.last().id;f.repository.select(b)
            f.repository.saveHome(a,HomeAppearance(language = HomeLanguage.ENGLISH,profileId = a))
            assertEquals(HomeLanguage.ENGLISH,f.repository.state.value.catalog.entries.first { it.id == a }.home?.language)
            assertEquals(HomeLanguage.GERMAN,f.repository.state.value.catalog.active.home?.language)
            assertEquals(ProfileResult.OK,f.repository.remove(a))
            assertTrue(runCatching { f.repository.saveHome(a,HomeAppearance(profileId = a)) }.isFailure)
            assertTrue(a in f.purged)
        }
    }
    @Test fun immutableBuiltinAndActiveProfileCannotBeRemovedAndNamesAndCapacityAreValidated() = runBlocking {
        ProfileFixture().use { f -> f.start();assertEquals(ProfileResult.ACTIVE_PROFILE,f.repository.remove("default"))
            assertEquals(ProfileResult.INVALID,f.repository.create(" ",ProfileKind.ADULT))
            assertEquals(ProfileResult.INVALID,f.repository.create("x".repeat(33),ProfileKind.ADULT))
            f.repository.create("Alpha",ProfileKind.ADULT);assertEquals(ProfileResult.INVALID,f.repository.create("ALPHA",ProfileKind.ADULT))
            repeat(6) { assertEquals(ProfileResult.OK,f.repository.create("$it",ProfileKind.ADULT)) }
            assertEquals(ProfileResult.LIMIT,f.repository.create("Overflow",ProfileKind.ADULT))
            val id = f.last().id;f.repository.select(id);assertEquals(ProfileResult.ACTIVE_PROFILE,f.repository.remove(id))
        }
    }
    @Test fun failedDeletionCleanupIsTombstonedAndRetriedWithoutRecreatingTheProfile() = runBlocking {
        ProfileFixture().use { f -> f.start();f.repository.create("A",ProfileKind.ADULT);val a = f.last().id
            f.failPurge = true;assertEquals(ProfileResult.OK,f.repository.remove(a))
            assertTrue(a in f.repository.state.value.catalog.retired);assertFalse(f.repository.state.value.catalog.entries.any { it.id == a })
            f.failPurge = false;f.repository.rename("default","Home")
            assertTrue(a in f.purged);assertTrue(f.repository.state.value.catalog.retired.isEmpty())
        }
    }
    @Test fun failedWriteKeepsThePreviouslySelectedProfile() = runBlocking {
        ProfileFixture().use { f -> f.start();f.repository.create("A",ProfileKind.ADULT);f.disk.fail = true
            assertEquals(ProfileResult.STORAGE,f.repository.select(f.last().id))
            assertEquals("default",f.repository.state.value.catalog.activeId);assertFalse(f.repository.state.value.switching)
        }
    }
    @Test fun unreadableRecordFailsClosedWithoutDeletingOrOverwritingIt() = runBlocking {
        val disk = ProfileMemory(mutablePreferencesOf(PROFILE_RECORD to "corrupt"))
        ProfileFixture(disk).use { f -> f.start();assertTrue(f.repository.state.value.failed)
            assertFalse(f.access.state.value.allows(ParentalSection.LIVE))
            assertEquals(ProfileResult.STORAGE,f.repository.create("A",ProfileKind.ADULT))
            assertEquals("corrupt",disk.values.value[PROFILE_RECORD])
        }
    }
    @Test fun switchingIsCommittedEvenIfTheOldScreenIsCancelledDuringDiskWrite() = runBlocking {
        ProfileFixture().use { f -> f.start();f.repository.create("A",ProfileKind.ADULT);val id = f.last().id
            val writing = CompletableDeferred<Unit>();val finish = CompletableDeferred<Unit>()
            f.disk.before = { writing.complete(Unit);finish.await() }
            val job = launch(Dispatchers.Default) { f.repository.select(id) };writing.await();job.cancel();finish.complete(Unit);job.join()
            assertEquals(id,f.repository.state.value.catalog.activeId);assertFalse(f.repository.state.value.switching)
        }
    }
}