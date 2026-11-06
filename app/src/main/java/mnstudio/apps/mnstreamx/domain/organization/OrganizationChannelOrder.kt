package mnstudio.apps.mnstreamx.domain.organization

import mnstudio.apps.mnstreamx.domain.model.Channel

internal object OrganizationChannelOrder {
    private val ultra = Regex("(?:^| )(?:4k|uhd|2160p)(?: |$)")
    private val full = Regex("(?:^| )(?:fhd|1080p)(?: |$)")
    private val high = Regex("(?:^| )hd(?: |$)")
    private val standard = Regex("(?:^| )sd(?: |$)")
    private val pattern = Regex("[0-9]+|[^0-9]+")
    internal data class Part(val text: String, val number: String?)
    internal data class NameKey(val parts: List<Part>, val quality: Int)
    private data class Key(val path: List<Int>, val name: NameKey)
    internal fun nameKey(name: String): NameKey {
            val quality = when {
                ultra.containsMatchIn(name) -> 0
                full.containsMatchIn(name) -> 1
                high.containsMatchIn(name) -> 2
                standard.containsMatchIn(name) -> 3
                else -> 4
            }
            val parts = pattern.findAll(OrganizationNames.canonicalNormalized(name)).map {
                val text = it.value
                Part(text, if (text.firstOrNull()?.isDigit() == true) text.trimStart('0').ifEmpty { "0" } else null)
            }.toList()
            return NameKey(parts, quality)
    }
    fun sort(channels: List<Channel>, names: Map<String, String>, orders: Map<String, Int>,
        metadata: OrganizationMetadata? = null, checkpoint: () -> Unit = {}): List<Channel> {
        val keys = channels.associate { c ->
            checkpoint()
            c.id to Key(c.folderIds.map { orders[it] ?: 0 }, metadata?.order(c.name) ?: nameKey(names.getValue(c.id)))
        }
        return channels.sortedWith { a, b ->
            checkpoint()
            val x = keys.getValue(a.id); val y = keys.getValue(b.id)
            comparePaths(x.path, y.path).takeIf { it != 0 } ?: compareNames(x.name.parts, y.name.parts).takeIf { it != 0 }
                ?: x.name.quality.compareTo(y.name.quality).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
    }
    private fun comparePaths(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val result = a[i].compareTo(b[i])
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }
    private fun compareNames(a: List<Part>, b: List<Part>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]
            val y = b[i]
            val result = if (x.number != null && y.number != null) {
                val u = x.number; val v = y.number
                u.length.compareTo(v.length).takeIf { it != 0 } ?: u.compareTo(v)
            } else x.text.compareTo(y.text)
            if (result != 0) return result
        }
        return a.size.compareTo(b.size)
    }
}