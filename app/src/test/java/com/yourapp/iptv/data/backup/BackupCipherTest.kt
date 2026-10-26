package com.yourapp.iptv.data.backup

import com.yourapp.iptv.domain.model.*
import java.nio.ByteBuffer
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class BackupCipherTest {
    private val cipher = BackupCipher()
    private fun password() = "Synthetic-عربي-ä-42".toCharArray()
    private val plain = "Synthetic backup / ملف تجريبي".toByteArray()
    @Test fun encryptionIsRandomizedAuthenticatedAndClearsTheSuppliedPassword() {
        val secret = password()
        val file = cipher.encrypt(plain, secret)
        assertTrue(secret.all { it == '\u0000' })
        assertFalse(file.contentEquals(cipher.encrypt(plain, password())))
        val unlock = password()
        assertArrayEquals(plain, cipher.decrypt(file, unlock)); assertTrue(unlock.all { it == '\u0000' })
        assertFalse(String(file, Charsets.ISO_8859_1).contains("Synthetic backup"))
    }
    @Test fun filesFromAnIndependentPythonPbkdf2AndAesGcmImplementationArePortable() {
        val vector = Base64.getDecoder().decode("TU5YQkFDSzEAAAABAAknwAABAgMEBQYHCAkKCwwNDg8gISIjJCUmJygpKisAAAAAnxnEkGWYAK4nR1t9rOilRZpQ0YDrAVPB+WWbNJavZOHdP482dK+daPUhysfuQUnNhSlAsvv9")
        assertArrayEquals(plain, cipher.decrypt(vector, password()))
    }
    @Test fun wrongPasswordsAndModifiedCiphertextOrSaltNeverReturnPlaintext() {
        val file = cipher.encrypt(plain, password())
        val badPassword = "Wrong-synthetic-password".toCharArray()
        assertEquals(BackupProblem.AUTHENTICATION, (runCatching { cipher.decrypt(file, badPassword) }.exceptionOrNull() as BackupException).problem)
        assertTrue(badPassword.all { it == '\u0000' })
        for (index in listOf(16, 35, file.lastIndex)) {
            val modified = file.copyOf(); modified[index] = (modified[index].toInt() xor 1).toByte()
            assertEquals(BackupProblem.AUTHENTICATION, (runCatching { cipher.decrypt(modified, password()) }.exceptionOrNull() as BackupException).problem)
        }
    }
    @Test fun untrustedVersionsAndKdfParametersAreRejectedBeforeKeyDerivation() {
        val file = cipher.encrypt(plain, password())
        ByteBuffer.wrap(file).putInt(12, Int.MAX_VALUE)
        assertEquals(BackupProblem.UNSUPPORTED, (runCatching { cipher.decrypt(file, password()) }.exceptionOrNull() as BackupException).problem)
        assertTrue(runCatching { cipher.decrypt(ByteArray(20), password()) }.isFailure)
    }
    @Test fun oversizedFilesAndInvalidPasswordsAreRejectedAndSecretsAreStillCleared() {
        val short = "short".toCharArray()
        assertEquals(BackupProblem.PASSWORD_RULES, (runCatching { cipher.encrypt(plain, short) }.exceptionOrNull() as BackupException).problem)
        assertTrue(short.all { it == '\u0000' })
        assertEquals(BackupProblem.TOO_LARGE, (runCatching { cipher.decrypt(ByteArray(BackupLimits.FILE + 1), password()) }.exceptionOrNull() as BackupException).problem)
    }
}