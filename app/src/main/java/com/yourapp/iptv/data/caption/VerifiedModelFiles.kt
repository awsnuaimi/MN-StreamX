package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import com.yourapp.iptv.domain.caption.CaptionStorageException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal data class ModelAsset(val path: String, val size: Long, val sha256: String)
internal data class ModelBundle(val id: String, val baseUrl: String, val assets: List<ModelAsset>) {
    val bytes: Long get() = assets.sumOf { it.size }
}

/** Pinned model bytes, bounded reads, atomic publication and cleanup on cancellation. */
internal class VerifiedModelFiles(private val directory: File, private val client: OkHttpClient,
    private val usableBytes: (File) -> Long = { it.usableSpace }) {
    private val mutex = Mutex()
    suspend fun prepare(bundle: ModelBundle, progress: (Int) -> Unit): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            val folder = File(directory, bundle.id)
            if (!folder.exists() && !folder.mkdirs()) throw IOException("Model directory unavailable")
            folder.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.forEach { it.delete() }
            val cached = bundle.assets.associateWith { valid(File(folder, it.path), it) }
            val missing = bundle.assets.filter { cached[it] != true }
            if (missing.isNotEmpty()) {
                missing.forEach { File(folder, it.path).delete() }
                requireStorage(folder, missing.sumOf { it.size } + CaptionResourcePolicy.STORAGE_RESERVE)
            }
            var completed = 0L
            for (asset in bundle.assets) {
                currentCoroutineContext().ensureActive()
                val file = File(folder, asset.path)
                if (cached[asset] != true) {
                    file.delete()
                    file.parentFile?.mkdirs()
                    requireStorage(folder, asset.size + CaptionResourcePolicy.STORAGE_RESERVE)
                    fetch(bundle.baseUrl + "/" + asset.path, file, asset) { downloaded ->
                        progress(((completed + downloaded) * 100 / bundle.bytes).toInt().coerceIn(0, 100))
                    }
                }
                completed += asset.size
                progress((completed * 100 / bundle.bytes).toInt())
            }
            folder
        }
    }
    private fun requireStorage(folder: File, required: Long) {
        val available = usableBytes(folder).coerceAtLeast(0)
        if (available < required) throw CaptionStorageException(available, required)
    }
    private suspend fun valid(file: File, asset: ModelAsset): Boolean {
        if (!file.isFile || file.length() != asset.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest()) == asset.sha256
    }
    private suspend fun fetch(url: String, target: File, asset: ModelAsset, progress: (Long) -> Unit) = coroutineScope {
        val temporary = File.createTempFile("caption-", ".part", target.parentFile)
        val call = client.newCall(Request.Builder().url(url).build())
        // Cancels the socket promptly even while execute/read is blocking on an IO worker.
        val guard = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Model download failed")
                val body = response.body ?: throw IOException("Empty model download")
                if (body.contentLength() > asset.size) throw IOException("Unexpected model size")
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                var lastProgress = -1048576L
                body.byteStream().use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > asset.size) throw IOException("Model exceeds its pinned size")
                        output.write(buffer, 0, read); digest.update(buffer, 0, read)
                        if (count - lastProgress >= 1048576) {
                            requireStorage(target.parentFile!!, asset.size - count + CaptionResourcePolicy.STORAGE_RESERVE)
                            progress(count); lastProgress = count
                        }
                    }
                } }
                if (count != asset.size || hex(digest.digest()) != asset.sha256) throw IOException("Model checksum mismatch")
                currentCoroutineContext().ensureActive()
                if (!temporary.renameTo(target)) throw IOException("Cannot publish verified model")
            }
        } catch (error: Exception) {
            // Socket closure after cancellation is cancellation, not a new download failure.
            currentCoroutineContext().ensureActive()
            throw error
        } finally { withContext(NonCancellable) { guard.cancelAndJoin(); temporary.delete() } }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}