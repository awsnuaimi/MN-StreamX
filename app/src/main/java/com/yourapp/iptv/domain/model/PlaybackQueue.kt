package com.yourapp.iptv.domain.model

class PlaybackQueue private constructor(val channels: List<Channel>) {
    private val positions = channels.withIndex().associate { it.value.id to it.index }
    val size: Int get() = channels.size

    fun indexOf(id: String?): Int = positions[id] ?: -1

    fun adjacentIndex(currentIndex: Int, direction: Int): Int {
        if (currentIndex !in channels.indices) return -1
        return Math.floorMod(currentIndex + direction, channels.size)
    }

    companion object {
        fun create(channels: List<Channel>, group: String? = null, query: String = "",
            favoritesOnly: Boolean = false): PlaybackQueue {
            val search = query.trim()
            return PlaybackQueue(channels.asSequence()
                .filter { !favoritesOnly || it.isFavorite }
                .filter { group == null || it.group == group }
                .filter { it.name.contains(search, ignoreCase = true) }
                .distinctBy { it.id }
                .toList())
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