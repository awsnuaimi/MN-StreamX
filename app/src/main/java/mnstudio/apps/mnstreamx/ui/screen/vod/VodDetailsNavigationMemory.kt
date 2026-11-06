package mnstudio.apps.mnstreamx.ui.screen.vod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** UI-only browse position. Each screen owns a source/profile-scoped instance. */
internal class VodDetailsNavigationMemory {
    private var positions by mutableStateOf<Map<String, VodDetailsPosition>>(emptyMap())

    fun get(seriesId: String): VodDetailsPosition? = positions[seriesId]

    fun selectSeason(seriesId: String, season: Int) {
        record(seriesId, VodDetailsPosition(season))
    }

    fun selectEpisode(seriesId: String, season: Int, episodeId: String) {
        if (episodeId.isNotBlank()) record(seriesId, VodDetailsPosition(season, episodeId))
    }

    private fun record(seriesId: String, position: VodDetailsPosition) {
        if (seriesId.isBlank() || seriesId.length > 256) return
        if (positions[seriesId] == position) return
        // A new selection goes to the end, so old items are evicted deterministically.
        positions = ((positions - seriesId) + (seriesId to position))
            .entries.toList().takeLast(LIMIT).associate { it.key to it.value }
    }

    fun entries(): List<Pair<String, VodDetailsPosition>> = positions.toList()

    companion object {
        const val LIMIT = 32
        fun restore(values: List<Any>): VodDetailsNavigationMemory = VodDetailsNavigationMemory().also { memory ->
            values.chunked(4).takeLast(LIMIT).forEach { parts ->
                if (parts.size != 4) return@forEach
                val id = parts[0] as? String ?: return@forEach
                val season = parts[1] as? Int ?: return@forEach
                val episode = parts[2] as? String ?: return@forEach
                val hasEpisode = parts[3] as? Boolean ?: return@forEach
                if (hasEpisode) memory.selectEpisode(id, season, episode)
                else memory.selectSeason(id, season)
            }
        }
    }
}

/** `sourceKey` already includes the selected profile and subscription scope. */
@Composable
internal fun rememberVodDetailsNavigationMemory(sourceKey: String?): VodDetailsNavigationMemory {
    val saver = listSaver<VodDetailsNavigationMemory, Any>(
        save = { memory -> memory.entries().flatMap { (id, position) ->
            listOf(id, position.season, position.episodeId.orEmpty(), position.episodeId != null)
        } },
        restore = { VodDetailsNavigationMemory.restore(it) }
    )
    return rememberSaveable(sourceKey, saver = saver) { VodDetailsNavigationMemory() }
}