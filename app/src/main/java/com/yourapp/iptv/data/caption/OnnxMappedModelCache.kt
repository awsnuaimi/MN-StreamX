package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import com.yourapp.iptv.domain.caption.CaptionStorageException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Bounded preparation, atomic directory publication, and checksums for graph and external weights. */
internal class OnnxMappedModelCache(
    private val export: suspend (File, File, File) -> Unit = OnnxExternalWeights::export,
    private val freeBytes: (File) -> Long = { it.usableSpace }
) {
    suspend fun prepare(source: File, sourceSha: String): File = lock.withLock {
        require(sourceSha.matches(Regex("[a-f0-9]{64}")))
        val folder = File(source.parentFile, source.nameWithoutExtension + "-mnx-mapped-v1-" + sourceSha.take(12))
        val model = File(folder, "model.onnx"); val weights = File(folder, "weights.bin")
        val manifest = File(folder, "checksum.txt")
        val text = manifest.takeIf { it.isFile && it.length() < 1024 }?.readText()
        val lines = text?.lines().orEmpty()
        if (lines.size == 6 && lines[0] == "mnx-external-v1" && lines[1] == sourceSha && model.isFile && weights.isFile &&
            lines[2] == model.length().toString() && lines[4] == weights.length().toString()) {
            val stamp = listOf(model.length().toString(), model.lastModified().toString(),
                weights.length().toString(), weights.lastModified().toString(), text!!)
            if (verified[folder.path] == stamp || (digest(model) == lines[3] && digest(weights) == lines[5])) {
                verified[folder.path] = stamp
                return@withLock model
            }
        }
        val required = source.length() + 8 * CaptionResourcePolicy.MIB + CaptionResourcePolicy.STORAGE_RESERVE
        val available = freeBytes(source.parentFile!!).coerceAtLeast(0)
        if (available < required) throw CaptionStorageException(available, required)
        val temporary = File.createTempFile("caption-mapped-", ".part", source.parentFile)
        check(temporary.delete() && temporary.mkdir())
        try {
            val graph = File(temporary, "model.onnx"); val data = File(temporary, "weights.bin")
            export(source, graph, data)
            currentCoroutineContext().ensureActive()
            check(graph.isFile && data.isFile && graph.length() > 0 && data.length() > 0)
            check(graph.length() + data.length() <= source.length() + 8 * CaptionResourcePolicy.MIB)
            File(temporary, "checksum.txt").writeText(listOf("mnx-external-v1", sourceSha,
                graph.length().toString(), digest(graph), data.length().toString(), digest(data)).joinToString("\n"))
            currentCoroutineContext().ensureActive()
            if (folder.exists()) check(folder.deleteRecursively()) { "Cannot remove invalid mapped-model cache" }
            check(temporary.renameTo(folder)) { "Cannot publish mapped-model cache" }
            verified[folder.path] = listOf(model.length().toString(), model.lastModified().toString(),
                weights.length().toString(), weights.lastModified().toString(), manifest.readText())
            model
        } finally { temporary.deleteRecursively() }
    }
    private suspend fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private companion object {
        val lock = Mutex()
        val verified = mutableMapOf<String, List<String>>()
    }
}