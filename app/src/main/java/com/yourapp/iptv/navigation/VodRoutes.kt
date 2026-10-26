package com.yourapp.iptv.navigation

import android.net.Uri
import com.yourapp.iptv.domain.model.VodKind

internal fun vodPlaybackRoute(kind: VodKind, id: String, extension: String, title: String,
    sourceKey: String? = null, seriesId: String? = null): String = buildString {
    val segment = if (kind == VodKind.MOVIE) "movie" else "series"
    val episode = kind == VodKind.SERIES && seriesId != null
    val path = if (episode) "vod-episode" else "vod-play"
    append("$path/$segment/${Uri.encode(id)}/${Uri.encode(extension)}?title=${Uri.encode(title)}")
    if (sourceKey != null) append("&sourceKey=${Uri.encode(sourceKey)}")
    if (episode) append("&seriesId=${Uri.encode(seriesId)}")
}