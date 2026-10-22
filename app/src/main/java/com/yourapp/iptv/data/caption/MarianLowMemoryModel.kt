package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.caption.CaptionResourcePolicy
import com.yourapp.iptv.domain.caption.CaptionStorageException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Builds a bounded, deterministic derivative of the verified model. No new model download. */
internal object MarianLowMemoryModel {
    private const val MAX_NODE = 2 * 1024 * 1024L
    private val mutex = Mutex()
    private var verified: Triple<String, Long, Long>? = null
    // Filled from the deterministic Kotlin transformer, not an unrelated ONNX serializer.
    internal const val OUTPUT_SHA256 = "94d1571d5266dd37a243f18fa920768b2e615ae70ed29c7f3ef904205df60bde"
    private data class Range(val header: Long, val start: Long, val end: Long)
    private data class Patch(val range: Range, val bytes: ByteArray)

    suspend fun prepare(folder: File): File = mutex.withLock {
        val source = folder.resolve("onnx/decoder_model_merged_quantized.onnx")
        val output = folder.resolve("onnx/decoder_model_mnx_blocks_v1.onnx")
        if (output.isFile) {
            val stamp = Triple(output.absolutePath, output.length(), output.lastModified())
            if (verified == stamp || sha(output) == OUTPUT_SHA256) { verified = stamp; return@withLock output }
        }
        val required = source.length() + 1024 * 1024 + CaptionResourcePolicy.STORAGE_RESERVE
        val available = folder.usableSpace.coerceAtLeast(0)
        if (available < required) throw CaptionStorageException(available, required)
        val temporary = File.createTempFile("marian-blocks-", ".part", output.parentFile)
        try {
            transform(source, temporary)
            check(sha(temporary) == OUTPUT_SHA256) { "Derived Marian checksum mismatch" }
            currentCoroutineContext().ensureActive()
            check(temporary.renameTo(output)) { "Cannot publish compact Marian model" }
            verified = Triple(output.absolutePath, output.length(), output.lastModified())
            output
        } finally { temporary.delete() }
    }
    internal suspend fun transform(source: File, target: File) {
        RandomAccessFile(source, "r").use { input ->
            var graph: Range? = null
            while (input.filePointer < input.length()) {
                val header = input.filePointer; val tag = CaptionProto.read(input)
                val range = field(input, header, tag, input.length())
                if (tag == 58L) { require(graph == null); graph = range }
                input.seek(range.end)
            }
            val container = checkNotNull(graph) { "Missing ONNX graph" }
            input.seek(container.start)
            val patches = mutableListOf<Patch>()
            while (input.filePointer < container.end) {
                currentCoroutineContext().ensureActive()
                val header = input.filePointer; val tag = CaptionProto.read(input)
                val range = field(input, header, tag, container.end)
                if (tag == 10L) {
                    require(range.end - range.start <= MAX_NODE)
                    val node = ByteArray((range.end - range.start).toInt()); input.readFully(node)
                    MarianBlockGraph.rewrite(node)?.let { patches += Patch(range, CaptionProto.message(1, it)) }
                }
                input.seek(range.end)
            }
            require(patches.size == 1) { "Unexpected Marian decoder graph" }
            val length = container.end - container.start + patches.sumOf { it.bytes.size - (it.range.end - it.range.header) }
            target.outputStream().buffered().use { output ->
                input.seek(0); copy(input, output, container.header)
                output.write(CaptionProto.varint(58)); output.write(CaptionProto.varint(length))
                input.seek(container.start)
                for (patch in patches) {
                    copy(input, output, patch.range.header - input.filePointer)
                    output.write(patch.bytes); input.seek(patch.range.end)
                }
                copy(input, output, input.length() - input.filePointer)
                output.write(CaptionProto.message(14, CaptionProto.string(1, "mn_streamx_modification") +
                    CaptionProto.string(2, "MN StreamX v29.2: float vocabulary head evaluated in blocks of 4096 columns; original weights retained.")))
            }
        }
    }
    private fun field(file: RandomAccessFile, header: Long, tag: Long, limit: Long): Range {
        require(tag ushr 3 > 0)
        val size = when ((tag and 7).toInt()) {
            0 -> { val start = file.filePointer; CaptionProto.read(file); return Range(header, start, file.filePointer) }
            1 -> 8L; 2 -> CaptionProto.read(file); 5 -> 4L; else -> error("Invalid ONNX field")
        }
        val start = file.filePointer; require(size in 0..(limit - start))
        return Range(header, start, start + size)
    }
    private suspend fun copy(input: RandomAccessFile, output: OutputStream, length: Long) {
        var left = length; val buffer = ByteArray(65536)
        while (left > 0) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            check(count > 0); output.write(buffer, 0, count); left -= count
        }
    }
    private suspend fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val size = input.read(buffer); if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}