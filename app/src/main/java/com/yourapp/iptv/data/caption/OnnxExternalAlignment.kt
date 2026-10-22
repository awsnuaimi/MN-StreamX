package com.yourapp.iptv.data.caption

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile

/** ORT's serializer does not page-align every tensor. Repack before publishing a mapped cache. */
internal object OnnxExternalAlignment {
    suspend fun prepare(model: File) {
        require(model.length() in 1..(8 * 1048576L))
        val source = File(model.parentFile, "weights.bin")
        val temporary = File(model.parentFile, "weights-aligned.part")
        try {
            val graph = RandomAccessFile(source, "r").use { input ->
                RandomAccessFile(temporary, "rw").use { output ->
                    output.setLength(0)
                    Repacker(input, output).model(model.readBytes())
                }
            }
            currentCoroutineContext().ensureActive()
            // Both files are still inside an unpublished cache directory; no session can map them.
            check(temporary.renameTo(source)) { "Cannot install aligned caption weights" }
            model.writeBytes(graph)
        } finally { temporary.delete() }
    }

    private class Repacker(private val input: RandomAccessFile, private val output: RandomAccessFile) {
        private val moved = mutableMapOf<Pair<Long, Long>, Long>()
        private val buffer = ByteArray(65536)
        private val maximum = input.length() + 32 * 1048576L

        suspend fun model(bytes: ByteArray): ByteArray {
            val rows = CaptionProto.fields(bytes)
            require(rows.count { it.number == 7 } == 1)
            val result = rows.map { if (it.number == 7) CaptionProto.message(7, graph(it.value)) else it.bytes }
            require(moved.isNotEmpty()) { "No external caption weights" }
            return CaptionProto.join(result)
        }
        private suspend fun graph(bytes: ByteArray): ByteArray = CaptionProto.join(CaptionProto.fields(bytes).map {
            when (it.number) {
                1 -> CaptionProto.message(1, node(it.value))
                5 -> CaptionProto.message(5, tensor(it.value))
                else -> it.bytes
            }
        })
        private suspend fun node(bytes: ByteArray): ByteArray = CaptionProto.join(CaptionProto.fields(bytes).map {
            if (it.number == 5) CaptionProto.message(5, attribute(it.value)) else it.bytes
        })
        private suspend fun attribute(bytes: ByteArray): ByteArray = CaptionProto.join(CaptionProto.fields(bytes).map {
            when (it.number) {
                5, 10 -> CaptionProto.message(it.number, tensor(it.value))
                6, 11 -> CaptionProto.message(it.number, graph(it.value))
                else -> it.bytes
            }
        })
        private suspend fun tensor(bytes: ByteArray): ByteArray {
            currentCoroutineContext().ensureActive()
            val rows = CaptionProto.fields(bytes)
            val entries = rows.filter { it.number == 13 }
            if (entries.isEmpty()) return bytes
            val data = entries.associate { field ->
                val entry = CaptionProto.fields(field.value)
                CaptionProto.text(entry, 1).single() to CaptionProto.text(entry, 2).single()
            }
            require(data["location"] == "weights.bin" && data.size == entries.size)
            val offset = checkNotNull(data["offset"]).toLong()
            val length = checkNotNull(data["length"]).toLong()
            require(offset >= 0 && length > 0 && length <= input.length() && offset <= input.length() - length)
            val range = offset to length
            val aligned = moved[range] ?: copy(offset, length).also { moved[range] = it }
            return CaptionProto.join(rows.map { row ->
                if (row.number != 13) row.bytes else {
                    val entry = CaptionProto.fields(row.value)
                    if (CaptionProto.text(entry, 1) != listOf("offset")) row.bytes else
                        CaptionProto.message(13, CaptionProto.join(entry.map {
                            if (it.number == 2) CaptionProto.string(2, aligned.toString()) else it.bytes
                        }))
                }
            })
        }
        private suspend fun copy(offset: Long, length: Long): Long {
            val at = output.length() + (16384 - output.length() % 16384) % 16384
            require(length <= maximum && at <= maximum - length) { "Aligned caption weights exceed limit" }
            output.seek(output.length())
            output.write(ByteArray((at - output.length()).toInt()))
            input.seek(offset)
            var remaining = length
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                val size = minOf(buffer.size.toLong(), remaining).toInt()
                input.readFully(buffer, 0, size); output.write(buffer, 0, size)
                remaining -= size
            }
            return at
        }
    }
}