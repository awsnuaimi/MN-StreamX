package com.yourapp.iptv.data.backup

import com.yourapp.iptv.domain.model.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BackupIoTest {
    @Test fun boundedReadsKeepAllBytesAndRejectOversizedProviderStreams() {
        assertArrayEquals(byteArrayOf(1,2,3), ByteArrayInputStream(byteArrayOf(1,2,3)).readBytesBounded(3))
        assertEquals(BackupProblem.TOO_LARGE, (runCatching {
            ByteArrayInputStream(byteArrayOf(1,2,3,4)).readBytesBounded(3)
        }.exceptionOrNull() as BackupException).problem)
    }
    @Test fun aBrokenProviderCannotSpinForeverReturningZeroBytes() {
        val broken = object : InputStream() {
            override fun read() = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int) = 0
        }
        assertEquals(BackupProblem.STORAGE, (runCatching { broken.readBytesBounded(100) }.exceptionOrNull() as BackupException).problem)
    }
    @Test fun theJsonBudgetRejectsAnOversizedValueBeforeBuildingTheSerializedDocument() {
        val payload = backupPayload()
        val huge = BackupValue(BackupValueType.STRING, "A".repeat(BackupLimits.PLAIN + 1))
        val changed = payload.copy(areas = payload.areas + (BackupStore.HOME to mapOf("language" to huge)))
        assertEquals(BackupProblem.TOO_LARGE, (runCatching { BackupPayloadCodec.encode(changed) }.exceptionOrNull() as BackupException).problem)
    }
}