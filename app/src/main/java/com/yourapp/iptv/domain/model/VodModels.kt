package com.yourapp.iptv.domain.model

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
    val year: String? = null
)

data class VodEpisode(
    val id: String,
    val name: String,
    val season: Int,
    val number: Int,
    val extension: String,
    val plot: String? = null
)

data class VodSeason(val number: Int, val name: String, val episodes: List<VodEpisode>)

data class VodDetails(val item: VodItem, val seasons: List<VodSeason> = emptyList())

data class VodCatalog(val categories: List<VodCategory>, val items: List<VodItem>)