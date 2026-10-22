package com.yourapp.iptv.data.caption

import ai.onnxruntime.OrtEnvironment
import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import com.yourapp.iptv.domain.caption.CaptionStorageException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Compile before listening, then reopen the checked local graph without re-running optimizers. */
internal class OnnxOptimizedModelCache(
    private val compile: suspend (File, File) -> Unit = { input, output ->
        MappedCaptionSession.open(OrtEnvironment.getEnvironment(), input, optimize = true,
            optimizedOutput = output).close()
        OnnxExternalAlignment.prepare(output)
    },
    private val runtimeSignature: () -> String = {
        "${OrtEnvironment.getEnvironment().version}:${System.getProperty("os.arch")}:cpu:extended:cse-off:no-prepack:aligned16k:v1"
    },
    private val freeBytes: (File) -> Long = { it.usableSpace }
) {
    suspend fun prepare(source: File, identity: String): File = lock.withLock {
        require(identity.matches(Regex("[a-f0-9]{64}")))
        val weights = File(source.parentFile, "weights.bin")
        require(source.isFile && source.length() in 1..(8 * CaptionResourcePolicy.MIB))
        require(weights.isFile && weights.length() in 1..(224 * CaptionResourcePolicy.MIB))
        val fingerprint = sha((identity + "\u0000" + runtimeSignature() + "\u0000" + digest(source)).toByteArray())
        val folder = File(source.parentFile, "optimized-" + fingerprint.take(24))
        val model = File(folder, "model.onnx")
        val data = File(folder, "weights.bin")
        val manifest = File(folder, "checksum.txt")
        val maximum = source.length() + weights.length() + 32 * CaptionResourcePolicy.MIB
        val text = manifest.takeIf { it.isFile && it.length() < 1024 }?.readText()
        val rows = text?.lines().orEmpty()
        if (rows.size == 6 && rows[0] == "mnx-optimized-v1" && rows[1] == fingerprint &&
            model.isFile && data.isFile && model.length() > 0 && data.length() > 0 &&
            model.length() <= 8 * CaptionResourcePolicy.MIB && model.length() + data.length() <= maximum &&
            rows[2] == model.length().toString() && rows[4] == data.length().toString()) {
            val stamp = listOf(model.length().toString(), model.lastModified().toString(),
                data.length().toString(), data.lastModified().toString(), text!!)
            if (verified[folder.path] == stamp || (digest(model) == rows[3] && digest(data) == rows[5])) {
                verified[folder.path] = stamp
                return@withLock model
            }
        }
        // Serialization and page-aligned repacking briefly coexist during preparation.
        val required = 2 * maximum + CaptionResourcePolicy.STORAGE_RESERVE
        val available = freeBytes(source.parentFile!!).coerceAtLeast(0)
        if (available < required) throw CaptionStorageException(available, required)
        val temporary = File.createTempFile("caption-optimized-", ".part", source.parentFile)
        check(temporary.delete() && temporary.mkdir())
        try {
            val output = File(temporary, "model.onnx")
            val outputWeights = File(temporary, "weights.bin")
            compile(source, output)
            currentCoroutineContext().ensureActive()
            check(output.isFile && outputWeights.isFile && output.length() in 1..(8 * CaptionResourcePolicy.MIB))
            check(outputWeights.length() > 0 && output.length() + outputWeights.length() <= maximum)
            File(temporary, "checksum.txt").writeText(listOf("mnx-optimized-v1", fingerprint,
                output.length().toString(), digest(output), outputWeights.length().toString(), digest(outputWeights)).joinToString("\n"))
            currentCoroutineContext().ensureActive()
            if (folder.exists()) check(folder.deleteRecursively()) { "Cannot replace invalid optimized cache" }
            check(temporary.renameTo(folder)) { "Cannot publish optimized caption cache" }
            verified[folder.path] = listOf(model.length().toString(), model.lastModified().toString(),
                data.length().toString(), data.lastModified().toString(), manifest.readText())
            model
        } finally { temporary.deleteRecursively() }
    }

    private suspend fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hex(hash.digest())
    }
    private fun sha(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        val lock = Mutex()
        val verified = mutableMapOf<String, List<String>>()
    }
}