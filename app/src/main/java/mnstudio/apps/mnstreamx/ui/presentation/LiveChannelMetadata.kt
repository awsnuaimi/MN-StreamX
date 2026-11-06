package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.Channel
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Bounded per-browser metadata. Favorites and folder memberships always come from fresh channels. */
internal class LiveChannelMetadataCache(private val capacity: Int = 12_000,
    private val prepare: (Channel) -> LiveChannelMetadata = LiveChannelMetadata.Companion::prepare) {
    private data class Identity(val id: String, val name: String, val group: String?, val url: String)
    private val values = LinkedHashMap<Identity, LiveChannelMetadata>(16, .75f, true)
    private val families = LinkedHashMap<List<String>, String>(16, .75f, true)
    private val digest = MessageDigest.getInstance("SHA-256")
    init { require(capacity in 1..20_000) }
    @Synchronized fun get(channel: Channel): LiveChannelMetadata {
        val key = Identity(channel.id, channel.name, channel.group, channel.streamUrl)
        return values[key] ?: prepare(channel).also { values[key] = it; trim(values) }
    }
    @Synchronized fun familyKey(family: List<String>): String = families[family] ?: run {
        val identity = buildString { family.forEach { append(it.length); append(':'); append(it) } }
        val hex = "0123456789abcdef"
        val result = buildString(64) {
            digest.digest(identity.toByteArray(Charsets.UTF_8)).forEach { byte ->
                val n = byte.toInt() and 255; append(hex[n ushr 4]); append(hex[n and 15])
            }
        }
        families[family] = result; trim(families); result
    }
    private fun <K,V> trim(map: LinkedHashMap<K,V>) {
        if (map.size > capacity) map.entries.iterator().apply { next(); remove() }
    }
}

internal data class LiveChannelMetadata(val title: String, val quality: String?, val family: List<String>) {
    companion object {
        private val prefix = Regex("^\\s*[A-Za-z]{2}\\s*[|:]\\s*")
        private val suffix = Regex("[\\s|:_-]+(?:8K|4K|UHD|FHD|HD|SD)$", RegexOption.IGNORE_CASE)
        fun title(channel: Channel): String = channel.name.trim().replaceFirst(prefix, "")
            .let { if (HomeChannelPresentation.quality(channel) != null) it.replace(suffix, "").trim() else it }
            .ifBlank { channel.name }
        fun prepare(channel: Channel): LiveChannelMetadata {
            val quality = HomeChannelPresentation.quality(channel)
            val title = title(channel)
            val family = if (quality == null) listOf("id",channel.id,channel.streamUrl) else listOf("quality",
                channel.group.orEmpty(),channel.name.trim().let { prefix.find(it)?.value?.trim().orEmpty() },
                Normalizer.normalize(title,Normalizer.Form.NFKC).lowercase(Locale.ROOT))
            return LiveChannelMetadata(title,quality,family)
        }
        fun category(value: String) = value.trim().replaceFirst(prefix, "").ifBlank { value }
    }
}