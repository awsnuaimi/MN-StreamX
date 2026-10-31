package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.VodItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Stateless selection; a caller can cancel scans when its account/query changes. */
internal object VodCatalogFilter {
    const val BACKGROUND_THRESHOLD = 400

    fun match(items: List<VodItem>, category: String?, query: String): List<VodItem> {
        val needle = query.trim()
        return items.filter { item ->
            (category == null || item.belongsTo(category)) &&
                (needle.isEmpty() || item.name.contains(needle, ignoreCase = true))
        }
    }

    suspend fun matchCancellable(items: List<VodItem>, category: String?, query: String): List<VodItem> {
        val context = currentCoroutineContext()
        val needle = query.trim()
        val output = ArrayList<VodItem>()
        items.forEachIndexed { index, item ->
            if ((index and 63) == 0) context.ensureActive()
            if ((category == null || item.belongsTo(category)) &&
                (needle.isEmpty() || item.name.contains(needle, ignoreCase = true))) {
                output.add(item)
            }
        }
        context.ensureActive()
        return output
    }
}