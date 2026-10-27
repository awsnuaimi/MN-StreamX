package com.yourapp.iptv.data.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Keeps the existing 12-byte IV + GCM ciphertext/tag format and each area's original AAD. */
internal class GcmRecordCipher(private val keys: StorageKey, aad: String, private val limit: Int) {
    private val associated = aad.toByteArray(Charsets.UTF_8)
    fun encrypt(plain: ByteArray, newRecord: Boolean): ByteArray {
        require(plain.size <= limit)
        return secureStorage {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, keys.get(newRecord)); updateAAD(associated)
                check(iv.size == 12)
                iv + doFinal(plain)
            }
        }
    }
    fun decrypt(record: ByteArray): ByteArray {
        decodeSecureRecord { require(record.size in 28..limit + 28) }
        return secureStorage {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, keys.get(false), GCMParameterSpec(128, record.copyOfRange(0, 12)))
                updateAAD(associated); doFinal(record, 12, record.size - 12)
            }
        }
    }
}

internal class TextRecordCipher(keys: StorageKey, aad: String, limit: Int, private val recordLimit: Int) {
    private val cipher = GcmRecordCipher(keys, aad, limit)
    fun encrypt(value: String, newRecord: Boolean): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return try { "gcm1:" + Base64.getEncoder().encodeToString(cipher.encrypt(bytes, newRecord)) }
        finally { bytes.fill(0) }
    }
    fun decrypt(value: String): String {
        val record = decodeSecureRecord {
            require(value.startsWith("gcm1:") && value.length <= recordLimit)
            Base64.getDecoder().decode(value.removePrefix("gcm1:"))
        }
        val plain = cipher.decrypt(record)
        return try { String(plain, Charsets.UTF_8) } finally { plain.fill(0) }
    }
}