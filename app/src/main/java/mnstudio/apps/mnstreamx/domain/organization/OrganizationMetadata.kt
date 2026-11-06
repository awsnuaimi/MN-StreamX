package mnstudio.apps.mnstreamx.domain.organization

/** Pure name computations, bounded and used only under OrganizationProjectionCache's mutex. */
internal class OrganizationMetadata(private val limit: Int = 12_000) {
    private class Entry {
        var voted = false
        var vote: String? = null
        var order: OrganizationChannelOrder.NameKey? = null
    }
    private val names = LinkedHashMap<String, Entry>(16, .75f, true)
    private fun entry(name: String): Entry = names[name] ?: Entry().also {
        names[name] = it
        while (names.size > limit) names.entries.iterator().apply { next(); remove() }
    }
    fun vote(name: String): String? = entry(name).let {
        if (!it.voted) { it.vote = ChannelLanguageDetector.vote(name); it.voted = true }
        it.vote
    }
    fun order(name: String): OrganizationChannelOrder.NameKey = entry(name).let {
        it.order ?: OrganizationChannelOrder.nameKey(OrganizationNames.normalize(name)).also { key -> it.order = key }
    }
}