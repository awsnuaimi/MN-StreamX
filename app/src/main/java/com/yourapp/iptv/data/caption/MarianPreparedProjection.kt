package com.yourapp.iptv.data.caption

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile

/** Exact uint8 weight layout cached once; float projection and vocabulary remain unchanged. */
internal object MarianPreparedProjection {
    const val TOKEN_OUTPUT = "mnx.next_token"
    private const val WEIGHT = "model.shared.weight_merged_0_quantized"
    private const val TRANSPOSED = "model.shared.weight_transposed_quantized"
    private const val PREFIX = "mnx.blockhead.v1."
    private const val VOCAB = 64110
    private const val WIDTH = 512
    private const val BLOCK = 4096
    private const val MAX_GRAPH = 4 * 1024 * 1024

    suspend fun export(source: File, model: File, weights: File) {
        OnnxExternalWeights.export(source, model, weights)
        check(model.length() in 1..MAX_GRAPH.toLong()) { "Unexpected Marian graph size" }
        val original = model.readBytes()
        val graph = CaptionProto.fields(CaptionProto.fields(original).single { it.number == 7 }.value)
        val tensors = graph.filter { it.number == 5 }.map { CaptionProto.fields(it.value) }
        val tensor = tensors.single { CaptionProto.text(it, 8) == listOf(WEIGHT) }
        require(tensor.filter { it.number == 1 }.map { integer(it.value) } == listOf(VOCAB.toLong(), WIDTH.toLong()))
        require(integer(tensor.single { it.number == 2 }.value) == 2L) { "Expected uint8 vocabulary" }
        val data = tensor.filter { it.number == 13 }.associate { field ->
            val entry = CaptionProto.fields(field.value)
            CaptionProto.text(entry, 1).single() to CaptionProto.text(entry, 2).single()
        }
        require(data["location"] == weights.name)
        val offset = checkNotNull(data["offset"]).toLong()
        require(data["length"]?.toLong() == VOCAB.toLong() * WIDTH)
        require(offset >= 0 && offset <= weights.length() - VOCAB.toLong() * WIDTH)
        val initializers = mutableListOf<ByteArray>()
        RandomAccessFile(weights, "rw").use { file ->
            val originalLength = file.length()
            val input = ByteArray(BLOCK * WIDTH)
            val transposed = ByteArray(input.size)
            for ((index, start) in (0 until VOCAB step BLOCK).withIndex()) {
                currentCoroutineContext().ensureActive()
                val columns = minOf(BLOCK, VOCAB - start)
                val length = columns * WIDTH
                file.seek(offset + start.toLong() * WIDTH); file.readFully(input, 0, length)
                transposeInto(input, columns, WIDTH, transposed)
                file.seek(file.length())
                file.write(ByteArray(((16384 - file.length() % 16384) % 16384).toInt()))
                val at = file.filePointer
                check(at >= originalLength)
                file.write(transposed, 0, length)
                initializers += externalTensor("$PREFIX$index.q", columns, at, length, weights.name)
            }
        }
        currentCoroutineContext().ensureActive()
        model.writeBytes(rewrite(original, initializers))
    }

    internal suspend fun transpose(input: ByteArray, rows: Int, columns: Int): ByteArray {
        require(rows > 0 && columns > 0 && rows.toLong() * columns == input.size.toLong())
        val output = ByteArray(input.size)
        transposeInto(input, rows, columns, output)
        return output
    }

    private suspend fun transposeInto(input: ByteArray, rows: Int, columns: Int, output: ByteArray) {
        require(rows.toLong() * columns <= minOf(input.size, output.size))
        for (row in 0 until rows) {
            if (row % 128 == 0) currentCoroutineContext().ensureActive()
            for (column in 0 until columns) output[column * rows + row] = input[row * columns + column]
        }
    }

    internal fun rewrite(model: ByteArray, initializers: List<ByteArray>): ByteArray {
        require(initializers.size == 16)
        val fields = CaptionProto.fields(model)
        require(fields.count { it.number == 7 } == 1)
        val result = fields.map { field ->
            if (field.number != 7) field.bytes else {
                val graph = CaptionProto.fields(field.value)
                require(graph.filter { it.number == 12 }.count {
                    CaptionProto.text(CaptionProto.fields(it.value), 1) == listOf("logits") } == 1)
                var branches = 0
                val rewritten = graph.map { row ->
                    when (row.number) {
                        1 -> {
                            val node = CaptionProto.fields(row.value)
                            if (CaptionProto.text(node, 4) != listOf("If")) row.bytes else {
                                branches++
                                CaptionProto.message(1, conditional(node))
                            }
                        }
                        12 -> if (CaptionProto.text(CaptionProto.fields(row.value), 1) == listOf("logits"))
                            CaptionProto.message(12, tokenType()) else row.bytes
                        else -> row.bytes
                    }
                }
                require(branches == 1) { "Unexpected Marian conditional" }
                val appended = initializers.map { CaptionProto.message(5, it) } + tokenNodes()
                CaptionProto.message(7, CaptionProto.join(rewritten + appended))
            }
        }
        val notice = CaptionProto.string(1, "mn_streamx_live_projection") + CaptionProto.string(2,
            "MN StreamX v29.5: pretransposed uint8 projection blocks and on-graph greedy token selection; original weights retained.")
        return CaptionProto.join(result) + CaptionProto.message(14, notice)
    }

    private fun conditional(node: List<CaptionProto.Field>): ByteArray {
        var branches = 0
        val rows = node.map { field ->
            if (field.number != 5) field.bytes else {
                val attribute = CaptionProto.fields(field.value)
                val name = CaptionProto.text(attribute, 1).singleOrNull()
                if (name !in setOf("then_branch", "else_branch")) field.bytes else {
                    require(attribute.count { it.number == 6 } == 1)
                    branches++
                    CaptionProto.message(5, CaptionProto.join(attribute.map {
                        if (it.number == 6) CaptionProto.message(6, branch(it.value)) else it.bytes
                    }))
                }
            }
        }
        require(branches == 2)
        return CaptionProto.join(rows)
    }

    private fun branch(bytes: ByteArray): ByteArray {
        var transposes = 0
        val slices = mutableSetOf<String>()
        val constants = mutableSetOf<String>()
        val expectedSlices = (0 until 16).map { "$PREFIX$it.q" }.toSet()
        val expectedConstants = setOf(PREFIX + "axis") + (0 until 16).flatMap {
            listOf("$PREFIX$it.start", "$PREFIX$it.end") }
        val rows = CaptionProto.fields(bytes).mapNotNull { field ->
            if (field.number != 1) field.bytes else {
                val node = CaptionProto.fields(field.value)
                val op = CaptionProto.text(node, 4).singleOrNull()
                val outputs = CaptionProto.text(node, 2)
                val inputs = CaptionProto.text(node, 1)
                when {
                    op == "Transpose" && outputs == listOf(TRANSPOSED) -> {
                        require(inputs == listOf(WEIGHT))
                        // The pinned transpose reverses its two dimensions without explicit attributes.
                        require(node.none { it.number == 5 })
                        transposes++; null
                    }
                    op == "Slice" && outputs.singleOrNull() in expectedSlices -> {
                        val output = outputs.single()
                        val base = output.removeSuffix("q")
                        require(inputs == listOf(TRANSPOSED, base + "start", base + "end", PREFIX + "axis"))
                        require(slices.add(output)); null
                    }
                    op == "Constant" && outputs.singleOrNull() in expectedConstants -> {
                        require(constants.add(outputs.single())); null
                    }
                    else -> field.bytes
                }
            }
        }
        require(transposes == 1 && slices == expectedSlices && constants == expectedConstants) {
            "Unexpected blocked Marian layout" }
        return CaptionProto.join(rows)
    }

    private fun tokenNodes(): List<ByteArray> {
        val prefix = "mnx.greedy."
        val end = constant(prefix + "end", VOCAB - 1L)
        val nodes = listOf(constant(prefix + "start", 0), end, constant(prefix + "axis", 2),
            node("Slice", listOf("logits", prefix + "start", prefix + "end", prefix + "axis"), prefix + "scores"))
        val axis = CaptionProto.string(1, "axis") + CaptionProto.integer(3, 2) + CaptionProto.integer(20, 2)
        val keep = CaptionProto.string(1, "keepdims") + CaptionProto.integer(3, 0) + CaptionProto.integer(20, 2)
        // Opset 11 ArgMax selects the first index on ties, matching the previous Kotlin loop.
        val argmax = node("ArgMax", listOf(prefix + "scores"), TOKEN_OUTPUT,
            CaptionProto.message(5, axis) + CaptionProto.message(5, keep))
        return (nodes + argmax).map { CaptionProto.message(1, it) }
    }

    private fun tokenType(): ByteArray {
        val dimension = CaptionProto.message(1, CaptionProto.integer(1, 1))
        val tensor = CaptionProto.integer(1, 7) + CaptionProto.message(2, dimension + dimension)
        return CaptionProto.string(1, TOKEN_OUTPUT) + CaptionProto.message(2, CaptionProto.message(1, tensor))
    }

    private fun externalTensor(name: String, columns: Int, offset: Long, length: Int, location: String): ByteArray {
        val entries = listOf("location" to location, "offset" to offset.toString(), "length" to length.toString())
            .map { (key, value) -> CaptionProto.message(13, CaptionProto.string(1, key) + CaptionProto.string(2, value)) }
        return CaptionProto.integer(1, WIDTH.toLong()) + CaptionProto.integer(1, columns.toLong()) +
            CaptionProto.integer(2, 2) + CaptionProto.string(8, name) + CaptionProto.join(entries) + CaptionProto.integer(14, 1)
    }

    private fun constant(name: String, value: Long): ByteArray {
        val tensor = CaptionProto.integer(1, 1) + CaptionProto.integer(2, 7) + CaptionProto.integer(7, value)
        val attribute = CaptionProto.string(1, "value") + CaptionProto.message(5, tensor) + CaptionProto.integer(20, 4)
        return node("Constant", emptyList(), name, CaptionProto.message(5, attribute))
    }

    private fun node(op: String, inputs: List<String>, output: String, attributes: ByteArray = byteArrayOf()) =
        CaptionProto.join(inputs.map { CaptionProto.string(1, it) }) + CaptionProto.string(2, output) +
            CaptionProto.string(3, "mnx.live.$output") + CaptionProto.string(4, op) + attributes

    private fun integer(bytes: ByteArray): Long {
        var result = 0L
        for ((index, byte) in bytes.withIndex()) {
            require(index < 9)
            val value = byte.toInt() and 255
            result = result or ((value and 127).toLong() shl (index * 7))
            if (value < 128) { require(index == bytes.lastIndex); return result }
        }
        error("Truncated initializer dimension")
    }
}