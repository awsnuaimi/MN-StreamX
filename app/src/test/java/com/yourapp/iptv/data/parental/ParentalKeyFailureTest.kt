package com.yourapp.iptv.data.parental

import android.app.Application
import android.security.keystore.KeyPermanentlyInvalidatedException
import com.yourapp.iptv.data.security.*
import com.yourapp.iptv.domain.parental.*
import com.yourapp.iptv.domain.security.SecureStorageFailure
import java.io.File
import java.io.IOException
import javax.crypto.Cipher
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
class ParentalKeyFailureTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun record() = ParentalRecord(ByteArray(16) { 1 },ByteArray(32) { 2 },ParentalRules())
    private fun disk(file: File, backend: FaultKeyBackend) = DeviceParentalStorage(file,backend.access())
    @Test fun missingAndInvalidatedKeysLeaveCiphertextUntouchedAndEverySectionLocked() = runTest {
        for (failure in listOf<Exception?>(null,KeyPermanentlyInvalidatedException())) {
            val file = temporary.newFile();file.delete()
            val keys = FaultKeyBackend();val storage = disk(file,keys);storage.write(record());val before = file.readBytes()
            if (failure == null) keys.value = null else keys.failure = failure
            val model = StoredParentalControl(storage,PinClock(),StandardTestDispatcher(testScheduler))
            model.foreground();model.initialize()
            val expected = if (failure == null) SecureStorageFailure.KEY_MISSING else SecureStorageFailure.KEY_INVALIDATED
            assertEquals(expected,model.state.value.storageFailure)
            assertTrue(model.state.value.configured);assertTrue(model.state.value.storageError)
            assertFalse(model.state.value.unlocked)
            ParentalSection.entries.forEach { assertFalse(model.state.value.allows(it)) }
            assertEquals(ParentalResult.DENIED,model.create("123456".toCharArray()))
            assertArrayEquals(before,file.readBytes());assertEquals(0,keys.creates)
        }
    }
    @Test fun temporaryKeystoreOutageCanBeRetriedWithoutResettingPinOrRules() = runTest {
        val file = File(temporary.root,"temporary");val keys = FaultKeyBackend();val storage = disk(file,keys)
        val salt = PinHash.salt();val rules = ParentalRules(ParentalSection.entries.toSet())
        storage.write(ParentalRecord(salt,PinHash.derive("123456".toCharArray(),salt),rules))
        val before = file.readBytes();keys.failure = IOException("Synthetic outage")
        val model = StoredParentalControl(storage,PinClock(),StandardTestDispatcher(testScheduler))
        model.foreground();model.initialize()
        assertEquals(SecureStorageFailure.TEMPORARILY_UNAVAILABLE,model.state.value.storageFailure)
        assertArrayEquals(before,file.readBytes())
        keys.failure = null;model.initialize()
        assertFalse(model.state.value.storageError);assertNull(model.state.value.storageFailure)
        assertEquals(rules,model.state.value.rules);assertFalse(model.state.value.unlocked)
        assertEquals(ParentalResult.OK,model.unlock("123456".toCharArray()));assertEquals(0,keys.creates)
    }
    @Test fun keyLossBetweenInitializationAndCommitRevokesTheGrantWithoutOverwritingProtection() = runTest {
        val file = File(temporary.root,"write");val keys = FaultKeyBackend();val storage = disk(file,keys)
        val model = StoredParentalControl(storage,PinClock(),StandardTestDispatcher(testScheduler))
        model.foreground();model.initialize();assertEquals(ParentalResult.OK,model.create("123456".toCharArray()))
        val before = file.readBytes();val creates = keys.creates;keys.value = null
        assertEquals(ParentalResult.STORAGE_ERROR,model.saveRules(ParentalRules(ParentalSection.entries.toSet())))
        assertEquals(SecureStorageFailure.KEY_MISSING,model.state.value.storageFailure)
        assertFalse(model.state.value.unlocked);assertFalse(model.state.value.allows(ParentalSection.LIVE))
        assertArrayEquals(before,file.readBytes());assertEquals(creates,keys.creates)
    }
    @Test fun invalidRecordAndAuthenticationFailureAreNotMisreportedAsKeyLoss() = runTest {
        val file = File(temporary.root,"invalid");val keys = FaultKeyBackend();val storage = disk(file,keys)
        storage.write(record());val bytes = file.readBytes();bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        val model = StoredParentalControl(storage,PinClock(),StandardTestDispatcher(testScheduler))
        model.initialize();assertEquals(SecureStorageFailure.AUTHENTICATION_FAILED,model.state.value.storageFailure)
        file.writeBytes(byteArrayOf(1,2));model.initialize()
        assertEquals(SecureStorageFailure.INVALID_RECORD,model.state.value.storageFailure)
        assertEquals(0,keys.creates);assertArrayEquals(byteArrayOf(1,2),file.readBytes())
    }
    @Test fun legacyParentalCiphertextStillOpensWithTheOriginalKeyAndFormat() {
        val file = File(temporary.root,"legacy");val keys = FaultKeyBackend()
        val encoded = ParentalRecordCodec.encode(record())
        file.writeBytes(Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE,keys.value);updateAAD("MN-StreamX/parental/v1".toByteArray())
            iv + doFinal(encoded)
        })
        assertEquals(record().rules,disk(file,keys).read()!!.rules);assertEquals(0,keys.creates)
    }
    @Test fun anAtomicBackupFileAlsoPreventsReplacementKeyCreation() {
        val file = File(temporary.root,"atomic");val keys = FaultKeyBackend();val storage = disk(file,keys)
        storage.write(record());val before = file.readBytes();val backup = File(file.path + ".bak")
        file.renameTo(backup);keys.value = null
        expectStorageFailure(SecureStorageFailure.KEY_MISSING) { storage.write(record()) }
        assertArrayEquals(before,backup.readBytes());assertEquals(0,keys.creates)
    }
    @Test fun firstRunDoesNotAccessKeystoreUntilProtectionIsExplicitlyConfigured() {
        val keys = FaultKeyBackend(null);val storage = disk(File(temporary.root,"first"),keys)
        assertNull(storage.read());assertEquals(0,keys.reads);assertEquals(0,keys.creates)
        storage.write(record());assertEquals(1,keys.creates);assertNotNull(storage.read())
    }
}