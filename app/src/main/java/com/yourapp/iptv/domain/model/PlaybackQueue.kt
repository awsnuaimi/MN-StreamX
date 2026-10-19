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
        fun create(channels: List<Channel>, group: String? = null, query: String = ""): PlaybackQueue {
            val search = query.trim()
            return PlaybackQueue(channels.asSequence()
                .filter { group == null || it.group == group }
                .filter { it.name.contains(search, ignoreCase = true) }
                .distinctBy { it.id }
                .toList())
        }

        fun empty() = PlaybackQueue(emptyList())
    }
}