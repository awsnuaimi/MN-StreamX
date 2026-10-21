package com.yourapp.iptv.data.cache

import com.yourapp.iptv.domain.organization.OrganizationConfig
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

internal object HomeCatalogKey {
    fun of(config: OrganizationConfig): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = object : OutputStream() { override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit }
        DataOutputStream(DigestOutputStream(sink, digest)).use { out ->
            out.writeUTF(config.sourceKey); out.writeBoolean(config.enabled)
            out.writeLong(config.revision); out.writeUTF(config.folderSort.name)
            out.writeInt(config.folders.size)
            config.folders.forEach {
                out.writeUTF(it.id); out.writeBoolean(it.parentId != null)
                it.parentId?.let(out::writeUTF)
                out.writeUTF(it.label?.name.orEmpty()); out.writeUTF(it.name); out.writeInt(it.order)
            }
            for (rules in listOf(config.groupOverrides, config.channelOverrides)) {
                out.writeInt(rules.size)
                rules.toSortedMap().forEach { (key, value) -> out.writeUTF(key); out.writeUTF(value) }
            }
        }
        val hex = "0123456789abcdef"
        return buildString(64) {
            digest.digest().forEach { val value = it.toInt() and 255
                append(hex[value ushr 4]); append(hex[value and 15]) }
        }
    }
}