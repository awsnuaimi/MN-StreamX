package com.yourapp.iptv.data.profile

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.yourapp.iptv.data.parental.*
import com.yourapp.iptv.data.security.*
import com.yourapp.iptv.domain.model.*
import com.yourapp.iptv.domain.parental.ParentalSection
import com.yourapp.iptv.domain.profile.*
import com.yourapp.iptv.domain.security.SecureStorageFailure
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProfileFailurePublicationTest {
    @Test fun aReadCompletedBeforeKeyLossCannotPublishHealthyStateAfterTheFailure() = runBlocking {
        val keys = FaultKeyBackend();val real = DeviceProfileCipher(keys.access())
        val disk = ProfileMemory(mutablePreferencesOf(PROFILE_RECORD to real.encrypt(ProfileCodec.encode(UserProfiles()))))
        val armed = AtomicBoolean(false);val entered = CountDownLatch(1);val release = CountDownLatch(1)
        val cipher = object : ProfileCipher by real {
            override fun decrypt(value: String): String {
                val plain = real.decrypt(value)
                if (armed.compareAndSet(true,false)) {
                    entered.countDown();check(release.await(5,TimeUnit.SECONDS))
                }
                return plain
            }
        }
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val pin = StoredParentalControl(PinMemory(),PinClock())
        val profiles = StoredProfiles(disk,cipher,pin,scope,{ HomeAppearance() to PlayerAppearance() })
        val access = ProfileParentalControl(pin,profiles)
        try {
            access.initialize();withContext(dispatcher) { }
            armed.set(true)
            disk.values.value = mutablePreferencesOf(PROFILE_RECORD to real.encrypt(ProfileCodec.encode(UserProfiles(revision = 1))))
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            keys.value = null
            assertEquals(ProfileResult.STORAGE,profiles.rename("default","Home"))
            assertEquals(SecureStorageFailure.KEY_MISSING,profiles.state.value.storageFailure)
            release.countDown();withContext(dispatcher) { }
            assertTrue(profiles.state.value.failed)
            assertEquals(SecureStorageFailure.KEY_MISSING,access.state.value.storageFailure)
            assertFalse(access.state.value.allows(ParentalSection.LIVE))
            assertEquals(0L,profiles.state.value.catalog.revision)
            keys.value = testStorageKey();access.initialize()
            assertFalse(profiles.state.value.failed);assertNull(profiles.state.value.storageFailure)
            assertEquals(1L,profiles.state.value.catalog.revision)
        } finally {
            release.countDown();scope.coroutineContext[Job]!!.cancelAndJoin();dispatcher.close()
        }
    }
}