package com.yourapp.iptv.data.backup

import com.yourapp.iptv.domain.model.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** A versioned, authenticated file. All header bytes are authenticated as GCM AAD. */
internal class BackupCipher {
    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        try {
            validatePassword(password)
            if (plain.size > BackupLimits.PLAIN) throw BackupException(BackupProblem.TOO_LARGE)
            val salt = ByteArray(16).also(random::nextBytes)
            val iv = ByteArray(12).also(random::nextBytes)
            val header = ByteBuffer.allocate(48).put(MAGIC).putInt(1).putInt(ITERATIONS).put(salt).put(iv).putInt(0).array()
            val encrypted = crypt(Cipher.ENCRYPT_MODE, plain, password, salt, iv, header)
            return header + encrypted
        } finally { password.fill('\u0000') }
    }
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        try {
            validatePassword(password)
            if (file.size > BackupLimits.FILE) throw BackupException(BackupProblem.TOO_LARGE)
            if (file.size < 64) throw BackupException(BackupProblem.INVALID)
            val header = file.copyOfRange(0, 48)
            val input = ByteBuffer.wrap(header)
            val magic = ByteArray(8).also(input::get)
            if (!magic.contentEquals(MAGIC)) throw BackupException(BackupProblem.INVALID)
            if (input.int != 1 || input.int != ITERATIONS) throw BackupException(BackupProblem.UNSUPPORTED)
            val salt = ByteArray(16).also(input::get)
            val iv = ByteArray(12).also(input::get)
            if (input.int != 0) throw BackupException(BackupProblem.INVALID)
            return try { crypt(Cipher.DECRYPT_MODE, file.copyOfRange(48, file.size), password, salt, iv, header) }
            catch (_: Exception) { throw BackupException(BackupProblem.AUTHENTICATION) }
        } finally { password.fill('\u0000') }
    }
    private fun crypt(mode: Int, bytes: ByteArray, password: CharArray, salt: ByteArray,
        iv: ByteArray, aad: ByteArray): ByteArray {
        val specification = PBEKeySpec(password, salt, ITERATIONS, 256)
        val keyBytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded }
            finally { specification.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
                updateAAD(aad); doFinal(bytes)
            }
        } finally { keyBytes.fill(0) }
    }
    companion object {
        const val ITERATIONS = 600_000
        private val MAGIC = "MNXBACK1".toByteArray(Charsets.US_ASCII)
        private val random = SecureRandom()
        fun validatePassword(password: CharArray) {
            if (password.size !in 12..128 || password.all(Char::isWhitespace)) throw BackupException(BackupProblem.PASSWORD_RULES)
        }
    }
}