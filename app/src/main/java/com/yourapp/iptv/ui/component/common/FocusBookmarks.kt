package com.yourapp.iptv.ui.component.common

/** Stable identifiers only. Focus nodes and catalog objects never enter saved state. */
internal class FocusBookmarks {
    data class Place(val key: String, val index: Int)
    private val places = linkedMapOf<String, Place>()
    val lastContext: String? get() = places.keys.lastOrNull()
    fun get(context: String): Place? = places[context]
    fun entries(): List<Pair<String, Place>> = places.toList()

    fun record(context: String, key: String, index: Int) {
        if (context.isBlank() || context.length > 1024 || key.isBlank() || key.length > 512) return
        places.remove(context)
        places[context] = Place(key, index.coerceAtLeast(0))
        while (places.size > LIMIT) places.remove(places.keys.first())
    }

    fun index(context: String, keys: List<String>, preferred: String? = null): Int? {
        return resolve(places[context], keys, preferred)
    }

    companion object {
        const val LIMIT = 32
        fun resolve(place: Place?, keys: List<String>, preferred: String? = null): Int? {
            if (keys.isEmpty()) return null
            val exact = keys.indexOf(preferred ?: place?.key)
            return exact.takeIf { it >= 0 } ?: (place?.index ?: 0).coerceIn(keys.indices)
        }
    }
}