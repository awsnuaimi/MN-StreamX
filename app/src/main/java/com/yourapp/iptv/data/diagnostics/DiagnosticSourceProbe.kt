package com.yourapp.iptv.data.diagnostics

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.yourapp.iptv.data.source.xtream.XtreamUrls
import com.yourapp.iptv.data.source.xtream.XtreamVodAccount
import com.yourapp.iptv.domain.diagnostics.*
import com.yourapp.iptv.domain.model.*
import java.io.StringReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class DiagnosticSourceProbe(private val transport: DiagnosticTransport) {
    fun kind(source: AppSettings) = when {
        !source.hasSelectedSource() -> DiagnosticSource.NONE
        source.source == PlaylistSource.XTREAM || XtreamVodAccount.resolve(source) != null -> DiagnosticSource.XTREAM
        else -> DiagnosticSource.M3U
    }
    suspend fun run(source: AppSettings, publish: (DiagnosticCheck) -> Unit) = withContext(Dispatchers.IO) {
        when (kind(source)) {
            DiagnosticSource.NONE -> publish(DiagnosticCheck(DiagnosticArea.ACCOUNT,DiagnosticStatus.NOTICE,DiagnosticIssue.NO_SOURCE))
            DiagnosticSource.M3U -> {
                val url = source.m3uUrl.trim().toHttpUrlOrNull()
                if (url == null) publish(invalid(DiagnosticArea.PLAYLIST))
                else publish(check(DiagnosticArea.PLAYLIST,url,64 * 1024,true) { response ->
                    val text = String(response.bytes,Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
                    if (text.startsWith("#EXTM3U") && text.getOrNull(7).let { it == null || it.isWhitespace() })
                        DiagnosticCheck(DiagnosticArea.PLAYLIST,DiagnosticStatus.PASS,DiagnosticIssue.PLAYLIST_HEADER)
                    else invalid(DiagnosticArea.PLAYLIST)
                })
            }
            DiagnosticSource.XTREAM -> {
                val account = XtreamVodAccount.resolve(source)
                if (account == null) { publish(invalid(DiagnosticArea.ACCOUNT));return@withContext }
                val urls = XtreamUrls(account.xtreamServer,account.xtreamUsername,account.xtreamPassword)
                val auth = check(DiagnosticArea.ACCOUNT,urls.api(),64 * 1024,parse = ::authentication)
                publish(auth)
                if ((auth.status == DiagnosticStatus.FAIL && auth.issue != DiagnosticIssue.INVALID_RESPONSE) ||
                    auth.issue == DiagnosticIssue.REDIRECT) return@withContext
                for ((area,action) in listOf(DiagnosticArea.LIVE_CATALOG to "get_live_categories",
                    DiagnosticArea.MOVIE_CATALOG to "get_vod_categories",DiagnosticArea.SERIES_CATALOG to "get_series_categories")) {
                    publish(check(area,urls.api(action),256 * 1024) { response -> categories(area,response) })
                }
            }
        }
    }
    private suspend fun check(area: DiagnosticArea, url: HttpUrl, limit: Int, prefix: Boolean = false,
        parse: (DiagnosticResponse) -> DiagnosticCheck): DiagnosticCheck {
        val started = System.nanoTime()
        return try {
            val response = transport.get(url,limit,prefix)
            val result = try {
                when {
                    response.status in 300..399 -> DiagnosticCheck(area,DiagnosticStatus.NOTICE,DiagnosticIssue.REDIRECT)
                    response.status !in 200..299 -> DiagnosticCheck(area,DiagnosticStatus.FAIL,DiagnosticIssue.HTTP)
                    response.bytes.isEmpty() -> DiagnosticCheck(area,DiagnosticStatus.FAIL,DiagnosticIssue.EMPTY_RESPONSE)
                    response.truncated && !prefix -> DiagnosticCheck(area,DiagnosticStatus.NOTICE,DiagnosticIssue.LIMIT)
                    else -> try { parse(response) }
                        catch (error: CancellationException) { throw error }
                        catch (_: com.yourapp.iptv.data.network.ResponseLimitFailure) { DiagnosticCheck(area,DiagnosticStatus.NOTICE,DiagnosticIssue.LIMIT) }
                        catch (_: Exception) { invalid(area) }
                }
            } finally { response.bytes.fill(0) }
            result.copy(httpStatus = response.status,elapsedMs = elapsed(started))
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val (issue,status) = DiagnosticFailures.classify(error)
            DiagnosticCheck(area,DiagnosticStatus.FAIL,issue,status,elapsed(started))
        }
    }
    private fun authentication(response: DiagnosticResponse): DiagnosticCheck {
        val root = DiagnosticJson.root(response.bytes)
        val user = root.getAsJsonObject("user_info") ?: return invalid(DiagnosticArea.ACCOUNT)
        val auth = user.get("auth")?.takeIf { it.isJsonPrimitive }?.asString
        val issue = when {
            auth == "0" || auth.equals("false",true) -> DiagnosticIssue.AUTH_REJECTED
            auth != "1" && !auth.equals("true",true) -> DiagnosticIssue.INVALID_RESPONSE
            user.get("status")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase(java.util.Locale.ROOT)
                in setOf("expired","disabled","banned","inactive") -> DiagnosticIssue.ACCOUNT_INACTIVE
            else -> DiagnosticIssue.OK
        }
        return DiagnosticCheck(DiagnosticArea.ACCOUNT,if (issue == DiagnosticIssue.OK) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,issue)
    }
    private fun categories(area: DiagnosticArea, response: DiagnosticResponse): DiagnosticCheck {
        var rows = 0
        val ids = HashSet<String>()
        JsonReader(StringReader(String(response.bytes,Charsets.UTF_8))).use { json ->
            require(json.peek() == JsonToken.BEGIN_ARRAY)
            json.beginArray()
            while (json.hasNext()) {
                if (++rows > 20_000) throw com.yourapp.iptv.data.network.ResponseLimitFailure()
                if (json.peek() != JsonToken.BEGIN_OBJECT) { json.skipValue();continue }
                var id: String? = null
                json.beginObject()
                while (json.hasNext()) {
                    val name = json.nextName()
                    if (name == "category_id" && json.peek() in setOf(JsonToken.STRING,JsonToken.NUMBER)) id = json.nextString().trim()
                    else json.skipValue()
                }
                json.endObject()
                id?.takeIf { it.length in 1..128 && it != "null" }?.let(ids::add)
            }
            json.endArray();require(json.peek() == JsonToken.END_DOCUMENT)
        }
        return DiagnosticCheck(area,if (ids.isEmpty()) DiagnosticStatus.NOTICE else DiagnosticStatus.PASS,DiagnosticIssue.CATEGORIES,count = ids.size)
    }
    private fun invalid(area: DiagnosticArea) = DiagnosticCheck(area,DiagnosticStatus.FAIL,DiagnosticIssue.INVALID_RESPONSE)
    private fun elapsed(start: Long) = ((System.nanoTime() - start) / 1_000_000).coerceIn(0,120_000)
}