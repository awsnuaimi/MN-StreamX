package com.yourapp.iptv.domain.model

class PlaybackQueue private constructor(val channels: List<Channel>) {
    private val positions = buildMap(channels.size) {
        channels.forEachIndexed { index, channel -> put(channel.id, index) }
    }
    val size: Int get() = channels.size

    fun indexOf(id: String?): Int = positions[id] ?: -1

    fun adjacentIndex(currentIndex: Int, direction: Int): Int {
        if (currentIndex !in channels.indices) return -1
        return Math.floorMod(currentIndex + direction, channels.size)
    }

    companion object {
        fun create(channels: List<Channel>, group: String? = null, query: String = "",
            favoritesOnly: Boolean = false): PlaybackQueue =
            PlaybackQueue(filterChannels(channels, group, query, favoritesOnly))

        /** Browsing needs filtered rows, not a second playback-position map. */
        fun filterChannels(channels: List<Channel>, group: String? = null, query: String = "",
            favoritesOnly: Boolean = false): List<Channel> {
            val search = query.trim()
            val folder = com.yourapp.iptv.domain.organization.FolderFilter.id(group)
            val ids = HashSet<String>()
            val result = ArrayList<Channel>()
            for (channel in channels) {
                if (favoritesOnly && !channel.isFavorite) continue
                if (group != null && channel.group != group && (folder == null || folder !in channel.folderIds)) continue
                if (search.isNotEmpty() && !channel.name.contains(search, ignoreCase = true)) continue
                if (ids.add(channel.id)) result.add(channel)
            }
            return result
        }

        fun empty() = PlaybackQueue(emptyList())
    }

    fun adjacentIdFrom(currentId: String?, direction: Int, original: PlaybackQueue): String? {
        if (channels.isEmpty()) return null
        val index = indexOf(currentId)
        if (index >= 0) return channels[adjacentIndex(index, direction)].id
        val originalIndex = original.indexOf(currentId)
        if (originalIndex >= 0) {
            for (offset in 1..original.size) {
                val candidate = original.channels[original.adjacentIndex(originalIndex, offset * direction)]
                if (indexOf(candidate.id) >= 0) return candidate.id
            }
        }
        return if (direction < 0) channels.last().id else channels.first().id
    }
}