package mnstudio.apps.mnstreamx.domain.model

enum class VodKind { MOVIE, SERIES }

data class VodCategory(val id: String, val name: String)

data class VodItem(
    val id: String,
    val name: String,
    val categoryId: String?,
    val poster: String? = null,
    val extension: String = "mp4",
    val plot: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val categoryIds: List<String> = listOfNotNull(categoryId)
) {
    fun belongsTo(category: String) = categoryId == category || category in categoryIds
}

data class VodEpisode(
    val id: String,
    val name: String,
    val season: Int,
    val number: Int,
    val extension: String,
    val plot: String? = null
)

data class VodSeason(val number: Int, val name: String, val episodes: List<VodEpisode>)

/** Ephemeral playback data: never put this URL into navigation arguments or watch history. */
data class VodPlaybackSource(val url: String, val extension: String) {
    override fun toString() = "VodPlaybackSource(extension=$extension)"
}

data class VodDetails(val item: VodItem, val seasons: List<VodSeason> = emptyList(),
    val playback: VodPlaybackSource? = null)

data class VodCatalog(
    val categories: List<VodCategory>,
    val items: List<VodItem>,
    val categoryScoped: Boolean = false,
    val categoriesUnavailable: Boolean = false
)