package com.yourapp.iptv.data.parental

import android.app.Application
import com.yourapp.iptv.domain.parental.*
import java.io.File
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34],application = Application::class)
class DeviceParentalStorageTest {
    @get:Rule val folder = TemporaryFolder()
    private fun record() = ParentalRecord(ByteArray(16) { it.toByte() },ByteArray(32) { (it + 20).toByte() },
        ParentalRules(setOf(ParentalSection.SETTINGS),mapOf("a".repeat(64) to setOf("Protected",""))),5,31000,1,30000)
    private fun store(file: File) = DeviceParentalStorage(file) { SecretKeySpec(ByteArray(32) { 7 },"AES") }
    private fun rejects(action: () -> Unit) { try { action();fail("Invalid record accepted") } catch (expected: Exception) { assertNotNull(expected) } }
    @Test fun authenticatedEncryptionRoundTripsAndUsesANewIvForEachWrite() {
        val file = File(folder.root,"state");val store = store(file)
        assertNull(store.read());store.write(record());val first = file.readBytes();store.write(record())
        assertFalse(first.contentEquals(file.readBytes()))
        val loaded = requireNotNull(store.read());assertEquals(record().rules,loaded.rules)
        assertArrayEquals(record().hash,loaded.hash);assertEquals(5,loaded.failures)
        assertFalse(String(file.readBytes(),Charsets.ISO_8859_1).contains("Protected"))
    }
    @Test fun tamperedOrTruncatedCiphertextIsNotConvertedIntoDisabledProtection() {
        val file = File(folder.root,"state");val store = store(file);store.write(record())
        val bytes = file.readBytes();bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
        rejects { store.read() };assertTrue(file.exists())
        file.writeBytes(byteArrayOf(1,2));rejects { store.read() };assertTrue(file.exists())
    }
    @Test fun oversizedFilesAreRejectedBeforeDecryptionAndExplicitDisableRemovesOnlyThisState() {
        val file = File(folder.root,"state");val other = File(folder.root,"account").apply { writeText("preserve") }
        file.writeBytes(ByteArray(ParentalRecordCodec.LIMIT + 29));rejects { store(file).read() }
        store(file).write(null);assertNull(store(file).read());assertEquals("preserve",other.readText())
    }
    @Test fun binaryCodecRejectsTrailingDataUnknownVersionsAndUnboundedRules() {
        val valid = ParentalRecordCodec.encode(record())
        rejects { ParentalRecordCodec.decode(valid + byteArrayOf(1)) }
        valid[3] = 99;rejects { ParentalRecordCodec.decode(valid) }
        rejects { ParentalRecordCodec.encode(ParentalRecord(ByteArray(15),ByteArray(32),ParentalRules())) }
        rejects { ParentalRules(groups = mapOf("a".repeat(64) to (1..513).map { it.toString() }.toSet())).validate() }
    }
}