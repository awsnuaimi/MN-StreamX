package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.domain.model.VodCategory
import com.yourapp.iptv.domain.model.VodItem

/** Derive fallback categories without allocating an intermediate list per title. */
internal object VodCategoryFallback {
    fun fromItems(items: List<VodItem>): List<VodCategory> {
        val ids = LinkedHashSet<String>()
        items.forEach { item -> item.categoryIds.forEach(ids::add) }
        return ids.map { VodCategory(it, it) }
    }
}