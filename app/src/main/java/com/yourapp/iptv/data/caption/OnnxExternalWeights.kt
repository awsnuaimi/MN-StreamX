package com.yourapp.iptv.data.caption

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/** Stream large initializer bytes into external data; CPU ORT maps these weights without copying them. */
internal object OnnxExternalWeights {
    private data class Field(val tag: Long, val header: Long, val start: Long, val end: Long)
    private data class Patch(val field: Field, val bytes: ByteArray)
    suspend fun export(source: File, model: File, weights: File) {
        RandomAccessFile(source, "r").use { input ->
            val graph = fields(input, 0, input.length()).single { it.tag == 58L }
            val patches = mutableListOf<Patch>()
            var written = 0L
            weights.outputStream().buffered().use { output ->
                for (initializer in fields(input, graph.start, graph.end).filter { it.tag == 42L }) {
                    currentCoroutineContext().ensureActive()
                    val tensor = fields(input, initializer.start, initializer.end)
                    require(tensor.none { it.tag ushr 3 in 13L..14L }) { "Weights must be embedded in the pinned source" }
                    val raw = tensor.singleOrNull { it.tag == 74L } ?: continue
                    val length = raw.end - raw.start
                    if (length < 65536) continue
                    val padding = ((16384 - written % 16384) % 16384).toInt()
                    output.write(ByteArray(padding)); written += padding
                    val offset = written
                    input.seek(raw.start); copy(input, output, length); written += length
                    val metadata = ByteArrayOutputStream()
                    for (field in tensor.filter { it !== raw }) {
                        val size = field.end - field.header
                        require(size <= 65536)
                        input.seek(field.header)
                        val bytes = ByteArray(size.toInt()); input.readFully(bytes); metadata.write(bytes)
                    }
                    for ((key, value) in listOf("location" to weights.name, "offset" to offset.toString(), "length" to length.toString())) {
                        metadata.write(CaptionProto.message(13, CaptionProto.string(1, key) + CaptionProto.string(2, value)))
                    }
                    metadata.write(CaptionProto.integer(14, 1))
                    patches += Patch(initializer, CaptionProto.message(5, metadata.toByteArray()))
                }
            }
            require(patches.isNotEmpty()) { "No externalizable weights" }
            val graphLength = graph.end - graph.start + patches.sumOf { it.bytes.size - (it.field.end - it.field.header) }
            model.outputStream().buffered().use { output ->
                input.seek(0); copy(input, output, graph.header)
                output.write(CaptionProto.varint(58)); output.write(CaptionProto.varint(graphLength))
                input.seek(graph.start)
                for (patch in patches) {
                    copy(input, output, patch.field.header - input.filePointer)
                    output.write(patch.bytes); input.seek(patch.field.end)
                }
                copy(input, output, input.length() - input.filePointer)
            }
        }
    }
    private fun fields(file: RandomAccessFile, start: Long, end: Long): List<Field> {
        val fields = mutableListOf<Field>(); file.seek(start)
        while (file.filePointer < end) {
            val header = file.filePointer; val tag = CaptionProto.read(file)
            require(tag ushr 3 > 0)
            val length = when ((tag and 7).toInt()) {
                0 -> { val value = file.filePointer; CaptionProto.read(file)
                    require(file.filePointer <= end)
                    fields += Field(tag, header, value, file.filePointer)
                    require(fields.size <= 20000); continue }
                1 -> 8L; 2 -> CaptionProto.read(file); 5 -> 4L; else -> error("Invalid ONNX wire field")
            }
            val value = file.filePointer; require(length in 0..(end - value))
            fields += Field(tag, header, value, value + length)
            require(fields.size <= 20000)
            file.seek(value + length)
        }
        return fields
    }
    private suspend fun copy(input: RandomAccessFile, output: OutputStream, length: Long) {
        require(length >= 0)
        var left = length; val buffer = ByteArray(65536)
        while (left > 0) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
            check(count > 0); output.write(buffer, 0, count); left -= count
        }
    }
}