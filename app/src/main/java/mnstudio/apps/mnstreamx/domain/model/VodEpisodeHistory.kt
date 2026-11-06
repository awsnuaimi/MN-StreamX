package mnstudio.apps.mnstreamx.domain.model

/** Completed episodes are metadata only; never store stream URLs or credentials. */
data class VodCompletedEpisode(val id: String, val seriesId: String, val updatedAtMs: Long)

data class VodProgressSnapshot(
    val entries: List<VodProgressEntry> = emptyList(),
    val completed: List<VodCompletedEpisode> = emptyList()
)

/** Bookmarks and completion markers share one atomic, profile-scoped record. */
object VodEpisodeHistory {
    const val LIMIT = 150

    fun valid(row: VodCompletedEpisode): Boolean =
        VodEpisodeOrder.validId(row.id) && VodEpisodeOrder.validId(row.seriesId) && row.updatedAtMs >= 0

    fun record(before: VodProgressSnapshot, entry: VodProgressEntry, completed: Boolean): VodProgressSnapshot {
        require(VodProgress.isValid(entry))
        if (entry.kind == VodKind.SERIES && before.entries.any { it.kind == entry.kind &&
            it.id == entry.id && it.updatedAtMs > entry.updatedAtMs }) return before
        val prior = before.completed.firstOrNull { it.id == entry.id && it.seriesId == entry.seriesId }
        if (prior != null && prior.updatedAtMs > entry.updatedAtMs) return before
        val bookmarks = VodProgress.record(before.entries, entry, completed)
        if (entry.kind != VodKind.SERIES || entry.seriesId == null) return before.copy(entries = bookmarks)
        val remaining = before.completed.filterNot { it.id == entry.id && it.seriesId == entry.seriesId }
        val next = if (completed) listOf(VodCompletedEpisode(entry.id, entry.seriesId, entry.updatedAtMs)) + remaining
            else remaining
        return VodProgressSnapshot(bookmarks, next.sortedByDescending { it.updatedAtMs }.take(LIMIT))
    }

    fun merge(local: VodProgressSnapshot, restored: VodProgressSnapshot): VodProgressSnapshot {
        val bookmarks = (local.entries + restored.entries).sortedByDescending { it.updatedAtMs }
            .distinctBy { it.kind to it.id }.take(VodProgress.LIMIT)
        val watched = (local.completed + restored.completed).sortedByDescending { it.updatedAtMs }
            .distinctBy { it.seriesId to it.id }.take(LIMIT)
        val active = bookmarks.filterNot { entry -> entry.kind == VodKind.SERIES &&
            watched.any { it.id == entry.id && it.seriesId == entry.seriesId && it.updatedAtMs >= entry.updatedAtMs } }
        val finished = watched.filterNot { marker -> active.any { entry ->
            entry.kind == VodKind.SERIES && entry.id == marker.id &&
                entry.seriesId == marker.seriesId && entry.updatedAtMs > marker.updatedAtMs } }
        return VodProgressSnapshot(active, finished)
    }
}