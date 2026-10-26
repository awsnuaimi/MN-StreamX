package com.yourapp.iptv.domain.model

/** Provider metadata only: never preloads video or resolves another account's URL. */
object VodEpisodeOrder {
    fun build(seasons: List<VodSeason>, active: () -> Unit = {}): List<VodEpisode> {
        val seen = HashSet<String>()
        val result = ArrayList<VodEpisode>()
        var count = 0
        for (season in seasons.sortedBy { it.number }) {
            if (season.number !in 0..999) continue
            for (episode in season.episodes.sortedBy { it.number }) {
                if (++count % 128 == 0) active()
                if (validId(episode.id) && episode.number in 0..9999 &&
                    episode.extension in extensions && seen.add(episode.id)) {
                    result.add(episode.copy(season = season.number))
                }
            }
        }
        active()
        return result
    }

    fun next(episodes: List<VodEpisode>, currentId: String): VodEpisode? {
        val index = episodes.indexOfFirst { it.id == currentId }
        return if (index < 0) null else episodes.getOrNull(index + 1)
    }

    fun validId(value: String) = value.length in 1..18 && value.all { it in '0'..'9' }
    private val extensions = setOf("mp4", "mkv", "avi", "mov", "ts", "m3u8", "webm")
}