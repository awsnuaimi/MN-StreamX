package com.yourapp.iptv.data.update

import com.yourapp.iptv.domain.update.AppUpdate
import com.yourapp.iptv.domain.update.UpdateFailure
import com.yourapp.iptv.domain.update.UpdateIssue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.security.MessageDigest

/** One bounded stream into private cache; an incomplete APK is never handed to the installer. */
internal class UpdateDownload(private val directory: File, private val http: UpdateHttp) {
    suspend fun fetch(update: AppUpdate, progress: (Int) -> Unit): File {
        if (update.bytes !in 1..AppUpdate.MAX_BYTES || !Regex("[0-9a-f]{64}").matches(update.sha256)) {
            throw UpdateFailure(UpdateIssue.INVALID_RELEASE)
        }
        if (!directory.exists() && !directory.mkdirs()) throw UpdateFailure(UpdateIssue.STORAGE)
        val destination = File(directory, "update-${update.versionCode}-${update.sha256.take(16)}.apk")
        val activity = currentCoroutineContext()
        if (matches(destination, update) { activity.ensureActive() }) { progress(100); return destination }
        destination.delete()
        directory.listFiles()?.filter { it != destination && it.extension in setOf("apk", "part") }?.forEach(File::delete)
        if (directory.usableSpace < update.bytes + 32L * 1024 * 1024) throw UpdateFailure(UpdateIssue.STORAGE)
        return http.get(update.url.toHttpUrl()) { response, active ->
            val body = response.body ?: throw UpdateFailure(UpdateIssue.INTEGRITY)
            if (body.contentLength() >= 0 && body.contentLength() != update.bytes) {
                throw UpdateFailure(UpdateIssue.INTEGRITY)
            }
            val temporary = File.createTempFile("update-", ".part", directory)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                var lastPercent = -1
                temporary.outputStream().buffered().use { output ->
                    body.byteStream().use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            active()
                            val read = stream.read(buffer)
                            if (read < 0) break
                            count += read
                            if (count > update.bytes) throw UpdateFailure(UpdateIssue.INTEGRITY)
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            val percent = (count * 100 / update.bytes).toInt()
                            if (percent != lastPercent) { progress(percent); lastPercent = percent }
                        }
                    }
                }
                active()
                if (count != update.bytes || hex(digest.digest()) != update.sha256) {
                    throw UpdateFailure(UpdateIssue.INTEGRITY)
                }
                if (!temporary.renameTo(destination)) throw UpdateFailure(UpdateIssue.STORAGE)
                destination
            } finally { temporary.delete() }
        }
    }

    companion object {
        internal fun matches(file: File, update: AppUpdate, active: () -> Unit = {}): Boolean {
            if (!file.isFile || file.length() != update.bytes) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    active()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return hex(digest.digest()) == update.sha256
        }
        internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}