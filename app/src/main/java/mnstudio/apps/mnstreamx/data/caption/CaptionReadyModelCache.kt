package mnstudio.apps.mnstreamx.data.caption

import ai.onnxruntime.OrtEnvironment
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Validate the final decoder first; successful publication retires reproducible intermediate weights. */
internal class CaptionReadyModelCache(private val signature: () -> String = {
    "${OrtEnvironment.getEnvironment().version}:${System.getProperty("os.arch")}:cpu:cse-off:aligned16k:language-v1"
}) {
    suspend fun prepare(source: File, identity: String, build: suspend () -> File): File = lock.withLock {
        require(source.isFile && identity.matches(Regex("[a-f0-9]{64}")))
        val root = source.parentFile!!.canonicalFile
        val prefix = source.nameWithoutExtension + "-mnx-ready-v1-"
        val fingerprint = digest((identity + "\u0000" + signature()).toByteArray())
        val folder = File(root, prefix + fingerprint.take(24))
        if (valid(folder, fingerprint)) {
            cleanup(root, source, folder)
            return@withLock File(folder, "model.onnx")
        }
        val built = build().canonicalFile
        val prepared = built.parentFile!!
        val intermediate = prepared.parentFile!!
        require(intermediate.parentFile?.canonicalFile == root &&
            intermediate.name.startsWith(source.nameWithoutExtension + "-mnx-mapped-") &&
            prepared.name.startsWith("optimized-") && built.name == "model.onnx")
        val weights = File(prepared, "weights.bin")
        require(built.isFile && weights.isFile && built.length() in 1..(8L * 1048576) &&
            weights.length() in 1..(224L * 1048576))
        val manifest = listOf("mnx-ready-v1", fingerprint, built.length().toString(), hash(built),
            weights.length().toString(), hash(weights)).joinToString("\n")
        currentCoroutineContext().ensureActive()
        if (folder.exists()) check(safe(folder, root) && folder.deleteRecursively())
        // Move rather than copy: the optimized graph's relative weights path remains valid.
        check(prepared.renameTo(folder)) { "Cannot publish ready translation model" }
        try {
            File(folder, "ready.txt").writeText(manifest)
            check(valid(folder, fingerprint))
            currentCoroutineContext().ensureActive()
            cleanup(root, source, folder)
            File(folder, "model.onnx")
        } catch (error: Throwable) {
            // An unverified final folder is never used on the next activation.
            throw error
        }
    }

    private suspend fun valid(folder: File, fingerprint: String): Boolean {
        if (!safe(folder, folder.parentFile!!.canonicalFile)) return false
        val note = File(folder, "ready.txt")
        val lines = note.takeIf { it.isFile && it.length() < 1024 }?.readText()?.lines().orEmpty()
        if (lines.size != 6 || lines[0] != "mnx-ready-v1" || lines[1] != fingerprint) return false
        val model = File(folder, "model.onnx"); val weights = File(folder, "weights.bin")
        if (!model.isFile || !weights.isFile || model.length() !in 1..(8L * 1048576) ||
            weights.length() !in 1..(224L * 1048576) || lines[2] != model.length().toString() ||
            lines[4] != weights.length().toString()) return false
        return hash(model) == lines[3] && hash(weights) == lines[5]
    }

    private fun cleanup(root: File, source: File, active: File) {
        val stem = source.nameWithoutExtension
        root.listFiles().orEmpty().filter { item ->
            item != active && item.isDirectory && safe(item, root) &&
                (item.name.startsWith("$stem-mnx-mapped-") || item.name.startsWith("$stem-mnx-ready-v1-"))
        }.forEach { it.deleteRecursively() }
    }

    private fun safe(file: File, root: File): Boolean = file.canonicalFile.parentFile == root &&
        file.canonicalFile.name == file.name

    private suspend fun hash(file: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                sha.update(buffer, 0, count)
            }
        }
        return hex(sha.digest())
    }
    private fun digest(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private companion object { val lock = Mutex() }
}