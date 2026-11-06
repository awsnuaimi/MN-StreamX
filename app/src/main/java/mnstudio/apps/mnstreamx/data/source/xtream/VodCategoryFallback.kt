package mnstudio.apps.mnstreamx.data.source.xtream

import mnstudio.apps.mnstreamx.domain.model.VodCategory
import mnstudio.apps.mnstreamx.domain.model.VodItem

/** Derive fallback categories without allocating an intermediate list per title. */
internal object VodCategoryFallback {
    fun fromItems(items: List<VodItem>): List<VodCategory> {
        val ids = LinkedHashSet<String>()
        items.forEach { item -> item.categoryIds.forEach(ids::add) }
        return ids.map { VodCategory(it, it) }
    }
}