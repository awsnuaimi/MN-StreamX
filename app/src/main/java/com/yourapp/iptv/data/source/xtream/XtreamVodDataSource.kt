package com.yourapp.iptv.data.source.xtream

import com.yourapp.iptv.data.network.HttpClient
import com.yourapp.iptv.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient

internal class XtreamVodDataSource(
    client: OkHttpClient = HttpClient.create(),
    private val catalogByteLimit: Int = 30 * 1024 * 1024
) {
    private val http = XtreamHttpClient(client)

    fun supports(settings: AppSettings) = XtreamVodAccount.resolve(settings) != null

    suspend fun catalog(settings: AppSettings, kind: VodKind): VodCatalog = supervisorScope {
        val urls = credentials(settings)
        val categories = async { attempt {
            http.read(urls.api(if (kind == VodKind.MOVIE) "get_vod_categories" else "get_series_categories"),
                3 * 1024 * 1024) { reader, active -> XtreamVodParser.categories(reader, active) }
        } }
        val streams = async { attempt {
            http.read(urls.api(if (kind == VodKind.MOVIE) "get_vod_streams" else "get_series"),
                catalogByteLimit) { reader, active -> XtreamVodParser.items(reader, kind, active) }
        } }
        val categoryResult = categories.await()
        val itemResult = streams.await()
        val items = itemResult.getOrNull().orEmpty()
        val folders = categoryResult.getOrNull().orEmpty()
        when {
            items.isNotEmpty() -> VodCatalog(folders.ifEmpty {
                items.flatMap { it.categoryIds }.distinct().map { VodCategory(it, it) }
            }, items, categoriesUnavailable = categoryResult.isFailure)
            folders.isNotEmpty() -> VodCatalog(folders, emptyList(), categoryScoped = true)
            itemResult.isFailure -> throw itemResult.exceptionOrNull()!!
            categoryResult.isFailure -> throw categoryResult.exceptionOrNull()!!
            else -> VodCatalog(emptyList(), emptyList())
        }
    }

    suspend fun category(settings: AppSettings, kind: VodKind, categoryId: String): List<VodItem> {
        require(categoryId.length in 1..128)
        val action = if (kind == VodKind.MOVIE) "get_vod_streams" else "get_series"
        val url = credentials(settings).api(action).newBuilder()
            .addQueryParameter("category_id", categoryId).build()
        return http.read(url, catalogByteLimit) { reader, active ->
            XtreamVodParser.items(reader, kind, active).map { item ->
                item.copy(categoryId = item.categoryId ?: categoryId,
                    categoryIds = (item.categoryIds + categoryId).distinct())
            }
        }
    }

    suspend fun details(settings: AppSettings, kind: VodKind, item: VodItem): VodDetails {
        val urls = credentials(settings)
        return http.read(urls.vodApi(if (kind == VodKind.MOVIE) "get_vod_info" else "get_series_info", item.id),
            8 * 1024 * 1024) { reader, _ -> XtreamVodParser.details(reader, kind, item) }
    }

    fun playbackUrl(settings: AppSettings, kind: VodKind, id: String, extension: String): String =
        credentials(settings).vodStream(kind, id, extension)

    private fun credentials(settings: AppSettings): XtreamUrls {
        val account = requireNotNull(XtreamVodAccount.resolve(settings)) { "Xtream account required" }
        return XtreamUrls(account.xtreamServer, account.xtreamUsername, account.xtreamPassword)
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try { Result.success(block()) }
    catch (error: CancellationException) { throw error }
    catch (error: Exception) { Result.failure(error) }
}