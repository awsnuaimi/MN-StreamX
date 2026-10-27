package com.yourapp.iptv.data.profile

import com.yourapp.iptv.data.parental.*
import com.yourapp.iptv.data.security.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.ParentalSection
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.security.SecureStorageFailure
import androidx.datastore.preferences.core.mutablePreferencesOf
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProfileKeyFailureTest {
    private class Fixture(val keys: FaultKeyBackend, val disk: ProfileMemory) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val pin = StoredParentalControl(PinMemory(),PinClock())
        val cipher = DeviceProfileCipher(keys.access())
        val profiles = StoredProfiles(disk,cipher,pin,scope,{ HomeAppearance() to PlayerAppearance() })
        val protection = ProfileParentalControl(pin,profiles)
        suspend fun close() { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun missingProfileKeyLocksTheProjectedProtectionWithoutReplacingTheCatalog() = runBlocking {
        val keys = FaultKeyBackend();val cipher = DeviceProfileCipher(keys.access())
        val record = cipher.encrypt(ProfileCodec.encode(UserProfiles()))
        val disk = ProfileMemory(mutablePreferencesOf(PROFILE_RECORD to record));keys.value = null
        val f = Fixture(keys,disk)
        try {
            f.protection.initialize()
            assertTrue(f.profiles.state.value.failed)
            assertEquals(SecureStorageFailure.KEY_MISSING,f.protection.state.value.storageFailure)
            ParentalSection.entries.forEach { assertFalse(f.protection.state.value.allows(it)) }
            assertEquals(ProfileResult.STORAGE,f.profiles.create("New",ProfileKind.ADULT))
            assertEquals(record,disk.values.value[PROFILE_RECORD]);assertEquals(0,keys.creates)
        } finally { f.close() }
    }
    @Test fun temporaryFailureRecoversTheOriginalProfileOnExplicitRetry() = runBlocking {
        val keys = FaultKeyBackend();val cipher = DeviceProfileCipher(keys.access())
        val people = UserProfiles(UserProfiles().entries + profileRow(),PROFILE_A)
        val record = cipher.encrypt(ProfileCodec.encode(people))
        val f = Fixture(keys,ProfileMemory(mutablePreferencesOf(PROFILE_RECORD to record)))
        try {
            keys.failure = IOException("Synthetic outage");f.protection.initialize()
            assertEquals(SecureStorageFailure.TEMPORARILY_UNAVAILABLE,f.profiles.state.value.storageFailure)
            keys.failure = null;f.protection.initialize()
            assertFalse(f.profiles.state.value.failed);assertNull(f.profiles.state.value.storageFailure)
            assertNull(f.protection.state.value.storageFailure);assertEquals(people,f.profiles.state.value.catalog)
            assertEquals(record,f.disk.values.value[PROFILE_RECORD]);assertEquals(0,keys.creates)
        } finally { f.close() }
    }
    @Test fun lossAfterDecodingButBeforeSaveCannotCreateANewProfileKey() = runBlocking {
        val keys = FaultKeyBackend();val f = Fixture(keys,ProfileMemory())
        try {
            f.protection.initialize();assertEquals(ProfileResult.OK,f.profiles.create("A",ProfileKind.ADULT))
            val before = f.disk.values.value[PROFILE_RECORD];val creates = keys.creates
            // The codec has read the old record; loss happens immediately before updateData commits.
            f.disk.before = { keys.value = null }
            assertEquals(ProfileResult.STORAGE,f.profiles.rename("default","Home"))
            assertEquals(SecureStorageFailure.KEY_MISSING,f.profiles.state.value.storageFailure)
            assertFalse(f.protection.state.value.allows(ParentalSection.LIVE))
            assertEquals(before,f.disk.values.value[PROFILE_RECORD]);assertEquals(creates,keys.creates)
        } finally { f.close() }
    }
    @Test fun authenticatedButInvalidCatalogIsARecordFailureAndIsPreserved() = runBlocking {
        val keys = FaultKeyBackend();val cipher = DeviceProfileCipher(keys.access());val record = cipher.encrypt("invalid")
        val f = Fixture(keys,ProfileMemory(mutablePreferencesOf(PROFILE_RECORD to record)))
        try {
            f.protection.initialize()
            assertEquals(SecureStorageFailure.INVALID_RECORD,f.profiles.state.value.storageFailure)
            assertEquals(record,f.disk.values.value[PROFILE_RECORD]);assertEquals(0,keys.creates)
        } finally { f.close() }
    }
    @Test fun retryReclassifiesTheCurrentFailureInsteadOfKeepingAnObsoleteDiagnosis() = runBlocking {
        val keys = FaultKeyBackend();val cipher = DeviceProfileCipher(keys.access())
        val record = cipher.encrypt(ProfileCodec.encode(UserProfiles()))
        val f = Fixture(keys,ProfileMemory(mutablePreferencesOf(PROFILE_RECORD to record)))
        try {
            keys.value = null;f.protection.initialize()
            assertEquals(SecureStorageFailure.KEY_MISSING,f.profiles.state.value.storageFailure)
            f.disk.values.value = mutablePreferencesOf(PROFILE_RECORD to "invalid")
            f.protection.initialize()
            assertEquals(SecureStorageFailure.INVALID_RECORD,f.profiles.state.value.storageFailure)
            assertFalse(f.protection.state.value.allows(ParentalSection.LIVE));assertEquals(0,keys.creates)
        } finally { f.close() }
    }

    @Test fun recoveringProfilesRequiresFreshPinVerificationInsteadOfReusingTheOldGrant() = runBlocking {
        val keys = FaultKeyBackend();val f = Fixture(keys,ProfileMemory())
        try {
            f.protection.foreground();f.protection.initialize()
            assertEquals(com.yourapp.iptv.domain.parental.ParentalResult.OK,f.pin.create("123456".toCharArray()))
            assertEquals(ProfileResult.OK,f.profiles.create("Adult",ProfileKind.ADULT))
            keys.failure = IOException("Synthetic profile outage")
            assertEquals(ProfileResult.STORAGE,f.profiles.rename("default","Home"))
            assertFalse(f.protection.state.value.unlocked)
            keys.failure = null;f.protection.initialize()
            assertFalse(f.protection.state.value.storageError);assertFalse(f.protection.state.value.unlocked)
            assertFalse(f.protection.state.value.allows(ParentalSection.LIVE))
            assertEquals(com.yourapp.iptv.domain.parental.ParentalResult.OK,f.protection.unlock("123456".toCharArray()))
            assertTrue(f.protection.state.value.allows(ParentalSection.LIVE));assertEquals(0,keys.creates)
        } finally { f.close() }
    }

}