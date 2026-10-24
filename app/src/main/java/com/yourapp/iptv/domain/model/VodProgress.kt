package com.yourapp.iptv.domain.model

/** Only metadata, never credentials or the resolved stream URL. */
data class VodProgressEntry(
    val kind: VodKind,
    val id: String,
    val title: String,
    val extension: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long
)

/** Consistent resume rules for movies and individual series episodes. */
object VodProgress {
    const val LIMIT = 50
    private const val MIN_RESUME_MS = 10_000L
    private const val MIN_DURATION_MS = 30_000L
    private const val END_GAP_MS = 30_000L

    fun isValid(entry: VodProgressEntry): Boolean =
        entry.id.length in 1..18 && entry.id.all { it in '0'..'9' } &&
            entry.title.isNotBlank() && entry.title.length <= 150 &&
            entry.extension in setOf("mp4", "mkv", "avi", "mov", "ts", "m3u8", "webm") &&
            entry.positionMs >= 0 && entry.durationMs > 0 &&
            entry.updatedAtMs >= 0

    fun resumable(entry: VodProgressEntry): Boolean = isValid(entry) &&
        entry.durationMs >= MIN_DURATION_MS && entry.positionMs >= MIN_RESUME_MS &&
        entry.positionMs < entry.durationMs - maxOf(END_GAP_MS, entry.durationMs / 20)

    /** Completion or playback near the end removes this entry from Continue Watching. */
    fun record(rows: List<VodProgressEntry>, entry: VodProgressEntry, completed: Boolean = false): List<VodProgressEntry> {
        require(isValid(entry))
        val kept = rows.filterNot { it.kind == entry.kind && it.id == entry.id }
        if (completed || !resumable(entry)) return kept.take(LIMIT)
        return (listOf(entry) + kept).sortedByDescending { it.updatedAtMs }.take(LIMIT)
    }

    fun find(rows: List<VodProgressEntry>, kind: VodKind, id: String): VodProgressEntry? =
        rows.firstOrNull { it.kind == kind && it.id == id && resumable(it) }
}