package mnstudio.apps.mnstreamx.viewmodel

import mnstudio.apps.mnstreamx.domain.model.VodCategory

/** Small, bounded hint; never fetches all categories or stores provider identifiers in diagnostics. */
internal object VodCategoryWarmup {
    const val MAX_ATTEMPTS = 3
    const val IDLE_DELAY_MS = 800L

    fun next(categories: List<VodCategory>, currentId: String,
        attempted: Set<String>): String? {
        if (categories.size < 2 || attempted.size >= MAX_ATTEMPTS) return null
        val index = categories.indexOfFirst { it.id == currentId }
        if (index < 0) return null
        // Prefer the next visible category; only fall back to the preceding one.
        val candidates = listOf(index + 1, index - 1)
        return candidates.asSequence().filter { it in categories.indices }
            .map { categories[it].id }
            .firstOrNull { it.isNotBlank() && it != currentId && it !in attempted }
    }
}