package com.yourapp.iptv.domain.model

/** One source/profile owner, bounded identifiers only. Search results never enter this memory. */
class HomeBrowseMemory {
    private data class Key(val group: String?, val favorites: Boolean)
    private val places = linkedMapOf<Key, HomeBrowseBookmark>()

    fun clear() = places.clear()

    fun load(value: HomeBrowseSession) {
        clear()
        value.bookmarks.takeLast(LIMIT).forEach(::put)
        remember(value)
    }

    fun find(group: String?, favorites: Boolean): HomeBrowseSession? =
        places[Key(group, favorites)]?.session()?.let(::decorate)

    fun decorate(value: HomeBrowseSession) = value.copy(bookmarks = places.values.toList())

    fun remember(value: HomeBrowseSession): HomeBrowseSession {
        if (!value.focusedChannelId.isNullOrBlank()) put(HomeBrowseBookmark(value.group,
            value.rootFolderId, value.favoritesOnly, value.focusedChannelId, value.position))
        return decorate(value)
    }

    private fun put(value: HomeBrowseBookmark) {
        if (!valid(value)) return
        val key = Key(value.group, value.favoritesOnly)
        places.remove(key); places[key] = value
        while (places.size > LIMIT) places.remove(places.keys.first())
    }

    companion object {
        const val LIMIT = 32
        fun valid(value: HomeBrowseBookmark): Boolean = !value.focusedChannelId.isNullOrBlank() &&
            (value.group?.length ?: 0) <= 4096 &&
            listOf(value.rootFolderId, value.focusedChannelId, value.position.anchorId).all { (it?.length ?: 0) <= 512 }
    }
}