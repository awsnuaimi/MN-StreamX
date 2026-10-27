package com.yourapp.iptv.data.parental

import android.util.AtomicFile
import com.yourapp.iptv.data.security.*
import java.io.File
import java.io.ByteArrayOutputStream
import javax.crypto.SecretKey

/** Keystore-protected, bounded, atomic state in noBackupFilesDir. No plaintext PIN is stored. */
internal class DeviceParentalStorage(private val file: File,
    keys: StorageKey = AndroidStorageKey("mnstreamx_parental_v1")) : ParentalStorage {
    constructor(file: File, key: () -> SecretKey) : this(file, ProvidedStorageKey(key))
    private val atomic = AtomicFile(file)
    private val cipher = GcmRecordCipher(keys, "MN-StreamX/parental/v1", ParentalRecordCodec.LIMIT)
    private fun exists() = file.exists() || File(file.path + ".bak").exists()
    override fun read(): ParentalRecord? = secureStorage {
        if (!exists()) return@secureStorage null
        val record = atomic.openRead().use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                decodeSecureRecord { require(count > 0 && out.size() + count <= ParentalRecordCodec.LIMIT + 28) }
                out.write(buffer,0,count)
            }
            out.toByteArray()
        }
        val plain = cipher.decrypt(record)
        try { decodeSecureRecord { ParentalRecordCodec.decode(plain) } } finally { plain.fill(0) }
    }
    override fun write(record: ParentalRecord?) {
        if (record == null) { atomic.delete(); check(!file.exists() && !File(file.path + ".bak").exists()); return }
        val plain = ParentalRecordCodec.encode(record)
        val encrypted = try { cipher.encrypt(plain, newRecord = !exists()) } finally { plain.fill(0) }
        check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
        val stream = atomic.startWrite()
        try { stream.write(encrypted); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}