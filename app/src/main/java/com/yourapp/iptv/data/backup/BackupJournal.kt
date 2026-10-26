package com.yourapp.iptv.data.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface BackupJournal {
    fun exists(): Boolean
    fun save(before: BackupSnapshot)
    fun read(): BackupSnapshot?
    fun clear()
}

/** An encrypted preimage survives process death; AtomicFile is used under AppDataGate. */
internal class DeviceBackupJournal(private val file: File, private val key: () -> SecretKey = ::journalKey) : BackupJournal {
    private val atomic = AtomicFile(file)
    private val aad = "MN-StreamX/restore-journal/v1".toByteArray(Charsets.UTF_8)
    override fun exists() = file.exists() || File(file.path + ".bak").exists()
    override fun save(before: BackupSnapshot) {
        check(!exists())
        val plain = BackupSnapshotCodec.encode(before)
        val encrypted = try { Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad)
            iv + doFinal(plain)
        } } finally { plain.fill(0) }
        file.parentFile?.mkdirs()
        val stream = atomic.startWrite()
        try { stream.write(encrypted); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
    override fun read(): BackupSnapshot? {
        if (!exists()) return null
        val bytes = atomic.openRead().use { input ->
            val result = input.readBytesBounded(BackupLimits.JOURNAL + 28)
            require(result.size >= 28); result
        }
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            updateAAD(aad); doFinal(bytes, 12, bytes.size - 12)
        }
        return try { BackupSnapshotCodec.decode(plain) } finally { plain.fill(0) }
    }
    override fun clear() { atomic.delete(); check(!exists() && !File(file.path + ".new").exists()) }
}

private fun journalKey(): SecretKey = synchronized(DeviceBackupJournal::class.java) {
    val alias = "mnstreamx_backup_journal_v1"
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
    }.generateKey()
}