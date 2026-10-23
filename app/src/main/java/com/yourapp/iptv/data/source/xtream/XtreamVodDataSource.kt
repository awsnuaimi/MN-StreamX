package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.network.HttpClient
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class XtreamVodDataSource {
    private val http = XtreamHttpClient(HttpClient.create())

    suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog = coroutineScope {
        val urls = credentials(settings)
        val categories = async {
            http.read(urls.api(if (kind == VodKind.MOVIE) "get_vod_categories" else "get_series_categories"),
                3 * 1024 * 1024) { reader, active -> XtreamVodParser.categories(reader, active) }
        }
        val streams = async {
            http.read(urls.api(if (kind == VodKind.MOVIE) "get_vod_streams" else "get_series"),
                30 * 1024 * 1024) { reader, active -> XtreamVodParser.items(reader, kind, active) }
        }
        VodCatalog(categories.await(), streams.await())
    }

    suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails {
        val urls = credentials(settings)
        return http.read(urls.vodApi(if (kind == VodKind.MOVIE) "get_vod_info" else "get_series_info", item.id),
            8 * 1024 * 1024) { reader, _ -> XtreamVodParser.details(reader, kind, item) }
    }

    fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String =
        credentials(settings).vodStream(kind, id, extension)

    private fun credentials(settings: AppSettings): XtreamUrls {
        require(settings.source == PlaylistSource.XTREAM && settings.hasSelectedSource()) {
            "Xtream account required for movies and series"
        }
        return XtreamUrls(settings.xtreamServer, settings.xtreamUsername, settings.xtreamPassword)
    }
}