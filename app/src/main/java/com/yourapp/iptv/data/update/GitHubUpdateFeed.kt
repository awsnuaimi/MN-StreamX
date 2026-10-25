package com.yourapp.iptv.data.update

import com.google.gson.JsonParser
import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayOutputStream

internal class GitHubUpdateFeed(private val repository: String, private val http: UpdateHttp) {
    suspend fun latest(installed: Long, abis: List<String>): AppUpdate? {
        if (!validRepository(repository)) throw UpdateFailure(UpdateIssue.NOT_CONFIGURED)
        val url = "https://api.github.com/repos/$repository/releases/latest".toHttpUrl()
        return http.get(url) { response, active ->
            val body = response.body ?: throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
            val limit = 512 * 1024
            if (body.contentLength() > limit) throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            body.byteStream().use { stream ->
                while (true) {
                    active()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > limit) throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
                    bytes.write(buffer, 0, count)
                }
            }
            // A nullable selection is wrapped because UpdateHttp's response value is non-null.
            Selection(parse(bytes.toString(Charsets.UTF_8.name()), repository, installed, abis))
        }.update
    }

    private data class Selection(val update: AppUpdate?)

    companion object {
        private val repoPattern = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}")
        private val versionPattern = Regex("v(1\\.1\\.([1-9][0-9]{0,7})\\.([1-9][0-9]?))")
        internal fun validRepository(repository: String) = repoPattern.matches(repository) &&
            repository.substringAfter('/') !in setOf(".", "..")

        internal fun parse(json: String, repository: String, installed: Long, abis: List<String>): AppUpdate? {
            try {
                if (!validRepository(repository)) throw UpdateFailure(UpdateIssue.NOT_CONFIGURED)
                val root = JsonParser.parseString(json).asJsonObject
                if (root["draft"].asBoolean || root["prerelease"].asBoolean) invalid()
                val tag = root["tag_name"].asString
                val match = versionPattern.matchEntire(tag) ?: invalid()
                val version = match.groupValues[1]
                val code = 100000L + match.groupValues[2].toLong() * 100 + match.groupValues[3].toLong()
                if (code > 2100000000L) invalid()
                if (code <= installed) return null
                val assets = root.getAsJsonArray("assets").map { it.asJsonObject }
                for (abi in abis.distinct()) {
                    if (abi !in setOf("arm64-v8a", "armeabi-v7a", "x86_64")) continue
                    val name = "MN-StreamX-$version-$abi.apk"
                    val candidates = assets.filter { it["name"]?.asString == name }
                    if (candidates.isEmpty()) continue
                    if (candidates.size != 1) invalid()
                    val asset = candidates.single()
                    val size = asset["size"].asLong
                    val digest = asset["digest"]?.asString ?: invalid()
                    if (asset["state"].asString != "uploaded" || size !in 1..AppUpdate.MAX_BYTES ||
                        !Regex("sha256:[0-9a-fA-F]{64}").matches(digest)) invalid()
                    val expected = "https://github.com".toHttpUrl().newBuilder()
                        .addPathSegments(repository).addPathSegments("releases/download")
                        .addPathSegment(tag).addPathSegment(name).build()
                    if (asset["browser_download_url"].asString != expected.toString()) invalid()
                    return AppUpdate(code, version, abi, expected.toString(), size,
                        digest.substringAfter(':').lowercase())
                }
                throw UpdateFailure(UpdateIssue.NO_COMPATIBLE_APK)
            } catch (error: UpdateFailure) { throw error }
            catch (_: Exception) { throw UpdateFailure(UpdateIssue.INVALID_RELEASE) }
        }

        private fun invalid(): Nothing = throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
    }
}