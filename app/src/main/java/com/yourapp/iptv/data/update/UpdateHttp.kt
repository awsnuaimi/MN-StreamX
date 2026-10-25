package com.yourapp.iptv.data.update

import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads on OkHttp's worker; cancellation closes the socket, including an active APK body. */
internal class UpdateHttp(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES).build(),
    private val trusted: (HttpUrl) -> Boolean = ::trustedGitHubUrl
) {
    private data class Result<T>(val value: T? = null, val redirect: HttpUrl? = null)

    suspend fun <T : Any> get(url: HttpUrl, read: (Response, () -> Unit) -> T): T {
        var next = url
        repeat(6) {
            if (!trusted(next)) throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
            val current = next
            val result = exchange(current) { response, active ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    val target = response.header("Location")?.let(current::resolve)
                        ?: throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
                    Result<T>(redirect = target)
                } else {
                    when (response.code) {
                        404 -> throw UpdateFailure(UpdateIssue.NO_RELEASE)
                        401 -> throw UpdateFailure(UpdateIssue.ACCESS)
                        403, 429 -> throw UpdateFailure(UpdateIssue.RATE_LIMIT)
                    }
                    if (!response.isSuccessful) throw UpdateFailure(UpdateIssue.NETWORK)
                    Result(value = read(response, active))
                }
            }
            result.value?.let { return it }
            next = result.redirect ?: throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
        }
        throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
    }

    private suspend fun <T> exchange(url: HttpUrl, read: (Response, () -> Unit) -> T): T =
        suspendCancellableCoroutine { pending ->
            val request = Request.Builder().url(url).header("User-Agent", "MN-StreamX-Updater")
                .header("Accept", if (url.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream").build()
            val call = client.newCall(request)
            if (url.host == "api.github.com") call.timeout().timeout(20, TimeUnit.SECONDS)
            pending.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (pending.isActive) pending.resumeWithException(UpdateFailure(UpdateIssue.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use { read(it) {
                            if (!pending.isActive) throw CancellationException("Update cancelled")
                        } }
                        if (pending.isActive) pending.resume(value)
                    } catch (error: Exception) {
                        if (pending.isActive) pending.resumeWithException(
                            error as? UpdateFailure ?: UpdateFailure(UpdateIssue.NETWORK))
                    }
                }
            })
        }

    companion object {
        internal fun trustedGitHubUrl(url: HttpUrl): Boolean = url.scheme == "https" &&
            url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
            url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com",
                "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    }
}