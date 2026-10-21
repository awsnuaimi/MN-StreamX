package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.model.HomeStartupSnapshot
import com.yourapp.iptv.domain.organization.OrganizationConfig
import com.yourapp.iptv.domain.organization.OrganizedChannels
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream

internal class HomeCatalogFile(private val file: File) {
    companion object { private const val MAX_BYTES = 64L * 1024 * 1024 }

    fun read(fingerprint: String, config: OrganizationConfig, checkpoint: () -> Unit): HomeStartupSnapshot? {
        if (!file.isFile || file.length() !in 1..MAX_BYTES) return null
        return FileInputStream(file).use { raw ->
            val checksum = CRC32()
            val input = DataInputStream(CheckedInputStream(BufferedInputStream(raw, 64 * 1024), checksum))
            val snapshot = HomeCatalogCodec.read(input, fingerprint, config, checkpoint)
            val actual = checksum.value
            require(input.readLong() == actual && input.read() == -1)
            snapshot
        }
    }

    fun write(fingerprint: String, config: OrganizationConfig, catalog: OrganizedChannels,
        refreshedAt: Long, checkpoint: () -> Unit) {
        val parent = requireNotNull(file.parentFile)
        require(parent.isDirectory || parent.mkdirs())
        val temporary = File.createTempFile("catalog-", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { raw ->
                val checksum = CRC32()
                val out = DataOutputStream(CheckedOutputStream(BufferedOutputStream(raw, 64 * 1024), checksum))
                HomeCatalogCodec.write(out, fingerprint, config, catalog, refreshedAt, checkpoint)
                out.writeLong(checksum.value); out.flush()
                require(temporary.length() <= MAX_BYTES)
                raw.fd.sync()
            }
            checkpoint()
            // Same filesystem rename: readers see either the previous complete file or the new one.
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}