package com.yourapp.iptv.data.security

import com.yourapp.iptv.domain.security.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*

internal class FaultKeyBackend(var value: SecretKey? = testStorageKey()) : StorageKeyBackend {
    var failure: Exception? = null
    var createFailure: Exception? = null
    var reads = 0
    var creates = 0
    override fun load(alias: String): SecretKey? {
        reads++; failure?.let { throw it }; return value
    }
    override fun create(alias: String): SecretKey {
        creates++; createFailure?.let { throw it }
        return testStorageKey().also { value = it }
    }
    fun access() = AndroidStorageKey("synthetic-encrypted-storage", this)
}
internal fun testStorageKey(seed: Int = 7): SecretKey = SecretKeySpec(ByteArray(32) { seed.toByte() }, "AES")
internal fun expectStorageFailure(expected: SecureStorageFailure, block: () -> Unit) {
    try { block(); fail("Encrypted storage operation unexpectedly succeeded") }
    catch (error: SecureStorageException) { assertEquals(expected, error.problem) }
}