package com.yourapp.iptv.viewmodel

import com.yourapp.iptv.domain.model.VodItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Stateless selection; a caller can cancel scans when its account/query changes. */
internal object VodCatalogFilter {
    const val BACKGROUND_THRESHOLD = 400

    /** A longer search can only remove matches from a completed result for the same list.
     * Identity checks also prevent reuse when subscriptions or category pages change.
     */
    fun refinedCandidates(
        items: List<VodItem>, category: String?, query: String,
        previousItems: List<VodItem>, previousCategory: String?, previousQuery: String,
        previousResult: List<VodItem>?
    ): List<VodItem> {
        val old = previousQuery.trim()
        val current = query.trim()
        return if (items === previousItems && category == previousCategory &&
            previousResult != null && old.isNotEmpty() &&
            current.length > old.length && current.startsWith(old, ignoreCase = true)) {
            previousResult
        } else items
    }

    fun match(items: List<VodItem>, category: String?, query: String): List<VodItem> {
        val needle = query.trim()
        if (category == null && needle.isEmpty()) return items
        return items.filter { item ->
            (category == null || item.belongsTo(category)) &&
                (needle.isEmpty() || item.name.contains(needle, ignoreCase = true))
        }
    }

    suspend fun matchCancellable(items: List<VodItem>, category: String?, query: String): List<VodItem> {
        val context = currentCoroutineContext()
        val needle = query.trim()
        if (category == null && needle.isEmpty()) return items
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