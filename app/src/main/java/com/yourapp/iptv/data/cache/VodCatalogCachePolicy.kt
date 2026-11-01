package com.yourapp.iptv.data.cache

import java.security.MessageDigest

/** Refresh policies are separate from the disk format so expiry remains easy to test. */
internal object VodCatalogCachePolicy {
    const val FRESH_MS = 12L * 60 * 60 * 1000
    const val OFFLINE_MS = 7L * 24 * 60 * 60 * 1000

    fun key(fingerprint: String, kind: String, categoryId: String? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in listOf(fingerprint, kind, categoryId?.let { "category:$it" } ?: "catalog")) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun fresh(savedAt: Long, now: Long): Boolean = age(savedAt, now)?.let { it < FRESH_MS } ?: false
    fun usableOffline(savedAt: Long, now: Long): Boolean = age(savedAt, now)?.let { it < OFFLINE_MS } ?: false
    private fun age(savedAt: Long, now: Long): Long? = (now - savedAt)
        .takeIf { savedAt > 0 && now >= savedAt }
}