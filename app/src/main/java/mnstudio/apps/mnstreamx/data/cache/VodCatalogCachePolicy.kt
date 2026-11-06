package mnstudio.apps.mnstreamx.data.cache

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
        val hex = "0123456789abcdef"
        return buildString(64) {
            digest.digest().forEach { byte ->
                val value = byte.toInt() and 255
                append(hex[value ushr 4]);append(hex[value and 15])
            }
        }
    }

    fun fresh(savedAt: Long, now: Long): Boolean = age(savedAt, now)?.let { it < FRESH_MS } ?: false
    fun usableOffline(savedAt: Long, now: Long): Boolean = age(savedAt, now)?.let { it < OFFLINE_MS } ?: false
    private fun age(savedAt: Long, now: Long): Long? = (now - savedAt)
        .takeIf { savedAt > 0 && now >= savedAt }
}