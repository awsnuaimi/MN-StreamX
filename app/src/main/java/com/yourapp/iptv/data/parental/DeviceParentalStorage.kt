package com.yourapp.iptv.data.parental

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore-protected, bounded, atomic state in noBackupFilesDir. No plaintext PIN is stored. */
internal class DeviceParentalStorage(private val file: File, private val key: () -> SecretKey = ::parentalKey) : ParentalStorage {
    private val atomic = AtomicFile(file)
    private val aad = "MN-StreamX/parental/v1".toByteArray(Charsets.UTF_8)
    override fun read(): ParentalRecord? {
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        val record = atomic.openRead().use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count > 0 && out.size() + count <= ParentalRecordCodec.LIMIT + 28)
                out.write(buffer,0,count)
            }
            out.toByteArray()
        }
        require(record.size in 28..ParentalRecordCodec.LIMIT + 28)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,record.copyOfRange(0,12)))
            updateAAD(aad); doFinal(record,12,record.size - 12)
        }
        return try { ParentalRecordCodec.decode(plain) } finally { plain.fill(0) }
    }
    override fun write(record: ParentalRecord?) {
        if (record == null) { atomic.delete(); check(!file.exists() && !File(file.path + ".bak").exists()); return }
        val plain = ParentalRecordCodec.encode(record)
        val encrypted = try { Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE,key()); updateAAD(aad); require(iv.size == 12); iv + doFinal(plain)
        } } finally { plain.fill(0) }
        check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
        val stream = atomic.startWrite()
        try { stream.write(encrypted); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}

private fun parentalKey(): SecretKey = synchronized(DeviceParentalStorage::class.java) {
    val alias = "mnstreamx_parental_v1"
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
    }.generateKey()
}