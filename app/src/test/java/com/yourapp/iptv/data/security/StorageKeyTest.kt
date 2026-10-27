package com.yourapp.iptv.data.security

import android.app.Application
import android.security.keystore.KeyPermanentlyInvalidatedException
import com.yourapp.iptv.domain.security.*
import java.io.IOException
import java.security.InvalidKeyException
import java.security.UnrecoverableKeyException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StorageKeyTest {
    @Test fun anAbsentKeyIsNeverCreatedByARead() {
        val backend = FaultKeyBackend(null)
        expectStorageFailure(SecureStorageFailure.KEY_MISSING) { backend.access().get(false) }
        assertEquals(0, backend.creates); assertNull(backend.value)
    }
    @Test fun confirmedFirstWriteCreatesOnceAndExistingKeysAreReusedAcrossInstances() {
        val backend = FaultKeyBackend(null)
        val key = backend.access().get(true)
        assertSame(key, backend.access().get(false)); assertSame(key, backend.access().get(true))
        assertEquals(1, backend.creates)
    }
    @Test fun providerFailureDoesNotBecomeMissingOrFallThroughToCreation() {
        val backend = FaultKeyBackend().apply { failure = IOException("Synthetic provider outage") }
        for (create in listOf(false,true)) {
            expectStorageFailure(SecureStorageFailure.TEMPORARILY_UNAVAILABLE) { backend.access().get(create) }
        }
        assertEquals(0, backend.creates)
        backend.failure = null; assertSame(backend.value, backend.access().get(false))
    }
    @Test fun unrecoverableKeyDoesNotByItselfProvePermanentInvalidation() {
        val backend = FaultKeyBackend().apply { failure = UnrecoverableKeyException("Synthetic provider failure") }
        expectStorageFailure(SecureStorageFailure.TEMPORARILY_UNAVAILABLE) { backend.access().get(true) }
        assertEquals(0, backend.creates)
    }
    @Test fun explicitInvalidationIncludingWrappedCausesNeverReplacesTheKey() {
        val backend = FaultKeyBackend().apply {
            failure = IOException("Synthetic wrapper", KeyPermanentlyInvalidatedException())
        }
        for (create in listOf(false,true)) {
            expectStorageFailure(SecureStorageFailure.KEY_INVALIDATED) { backend.access().get(create) }
        }
        assertEquals(0, backend.creates)
    }
    @Test fun creationFailureCanBeRetriedWithoutPublishingAFakeKey() {
        val backend = FaultKeyBackend(null).apply { createFailure = IOException("Synthetic generation failure") }
        expectStorageFailure(SecureStorageFailure.TEMPORARILY_UNAVAILABLE) { backend.access().get(true) }
        assertNull(backend.value)
        backend.createFailure = null; assertNotNull(backend.access().get(true)); assertEquals(2, backend.creates)
    }
    @Test fun concurrentFirstWritesThroughDifferentInstancesCannotGenerateTwoKeys() {
        val backend = FaultKeyBackend(null); val ready = CountDownLatch(2); val start = CountDownLatch(1)
        val threads = Executors.newFixedThreadPool(2)
        try {
            val futures = List(2) { threads.submit<javax.crypto.SecretKey> {
                ready.countDown(); check(start.await(5,TimeUnit.SECONDS)); backend.access().get(true)
            } }
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown()
            assertSame(futures[0].get(5,TimeUnit.SECONDS),futures[1].get(5,TimeUnit.SECONDS))
            assertEquals(1,backend.creates)
        } finally { start.countDown();threads.shutdownNow() }
    }
    @Test fun authenticationFailureIsNotReportedAsMissingOrPermanentKeyLoss() {
        assertEquals(SecureStorageFailure.AUTHENTICATION_FAILED,storageFailure(AEADBadTagException()))
        assertEquals(SecureStorageFailure.TEMPORARILY_UNAVAILABLE,storageFailure(InvalidKeyException()))
    }
    @Test fun cancellationIsNotConvertedIntoAStorageFailureOrNewKey() {
        val cancelled = CancellationException("Synthetic cancellation")
        val backend = FaultKeyBackend().apply { failure = cancelled }
        try { backend.access().get(true);fail("Cancellation swallowed") }
        catch (error: CancellationException) { assertSame(cancelled,error) }
        assertEquals(0,backend.creates)
    }
}