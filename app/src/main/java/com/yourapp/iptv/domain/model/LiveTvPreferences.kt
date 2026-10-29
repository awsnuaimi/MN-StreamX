package com.yourapp.iptv.domain.model

/** Personal organization only; parental permission is always checked separately. */
enum class LiveTvSort { PROVIDER, NAME, FAVORITES }
private val liveTvFamily = Regex("[a-f0-9]{64}")

data class LiveTvPreferences(
    val sort: LiveTvSort = LiveTvSort.PROVIDER,
    val hiddenChannels: Set<String> = emptySet(),
    val hiddenGroups: Set<String> = emptySet(),
    val hiddenFolders: Set<String> = emptySet(),
    val preferredQualities: Map<String, String> = emptyMap()
) {
    fun visible(channel: Channel) = channel.id !in hiddenChannels && channel.group !in hiddenGroups &&
        channel.folderIds.none { it in hiddenFolders }
    fun validate(): LiveTvPreferences {
        fun valid(values: Set<String>, count: Int, length: Int) {
            require(values.size <= count && values.all { it.isNotBlank() && it.length <= length })
        }
        valid(hiddenChannels, 10_000, 512); valid(hiddenGroups, 2_048, 256)
        valid(hiddenFolders, 2_048, 96)
        require(preferredQualities.size <= 10_000 && preferredQualities.all { (family, id) ->
            liveTvFamily.matches(family) && id.isNotBlank() && id.length <= 512
        })
        return this
    }
    fun prefer(family: String, selected: String?): LiveTvPreferences = copy(
        preferredQualities = preferredQualities.toMutableMap().apply {
            if (selected == null) remove(family) else put(family, selected)
        }).validate()
    fun showAll() = copy(hiddenChannels = emptySet(), hiddenGroups = emptySet(), hiddenFolders = emptySet())
    override fun toString() = "LiveTvPreferences(sort=$sort, hidden=${hiddenChannels.size}, groups=${hiddenGroups.size}, folders=${hiddenFolders.size})"
}