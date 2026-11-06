package mnstudio.apps.mnstreamx.domain.model

/** A suggestion only. Playback and automatic next-episode behavior remain explicit. */
enum class VodSeriesChoiceKind { RESUME, START, REWATCH }

data class VodSeriesChoice(
    val episode: VodEpisode,
    val kind: VodSeriesChoiceKind,
    val positionMs: Long = 0L
)

/** Chooses only provider-verified episodes from the selected series. No network or side effects. */
object VodSeriesContinuation {
    fun choose(seriesId: String, seasons: List<VodSeason>,
        progress: List<VodProgressEntry>, completed: List<VodCompletedEpisode>): VodSeriesChoice? {
        if (!VodEpisodeOrder.validId(seriesId)) return null
        val ordered = VodEpisodeOrder.build(seasons)
        if (ordered.isEmpty()) return null
        val allowed = ordered.associateBy { it.id }
        val latestCompleted = completed.asSequence().filter {
            it.seriesId == seriesId && it.id in allowed && VodEpisodeHistory.valid(it)
        }.groupBy { it.id }.mapValues { (_, events) -> events.maxOf { it.updatedAtMs } }
        val active = progress.asSequence().filter {
            it.kind == VodKind.SERIES && it.seriesId == seriesId &&
                it.id in allowed && VodProgress.resumable(it) &&
                it.updatedAtMs > (latestCompleted[it.id] ?: -1L)
        }.maxWithOrNull(compareBy<VodProgressEntry> { it.updatedAtMs }.thenBy { it.id })
        if (active != null) return VodSeriesChoice(allowed.getValue(active.id),
            VodSeriesChoiceKind.RESUME, active.positionMs)
        val unwatched = ordered.firstOrNull { it.id !in latestCompleted }
        if (unwatched != null) return VodSeriesChoice(unwatched, VodSeriesChoiceKind.START)
        return VodSeriesChoice(ordered.first(), VodSeriesChoiceKind.REWATCH)
    }
}