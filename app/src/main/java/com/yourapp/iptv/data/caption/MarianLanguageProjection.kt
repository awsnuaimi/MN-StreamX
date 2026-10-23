package com.yourapp.iptv.data.caption

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile

/** Stream the pinned uint8 head into transposed blocks; keep logits for bounded beam search. */
internal class MarianLanguageProjection(private val config: MarianModelConfig) {
    private val prefix = "mnx.language.head."
    private val matrix = "model.shared.weight_transposed_quantized"
    private val floatMatrix = "model.shared.weight_transposed"
    private val block = 4096
    private lateinit var weight: String
    private lateinit var scale: String
    private lateinit var zero: String

    suspend fun export(source: File, model: File, weights: File) {
        OnnxExternalWeights.export(source, model, weights)
        require(model.length() in 1..(8 * 1048576L))
        val original = model.readBytes()
        val root = CaptionProto.fields(original)
        val graph = CaptionProto.fields(root.single { it.number == 7 }.value)
        val tensors = graph.filter { it.number == 5 }.map { CaptionProto.fields(it.value) }
        val tensor = tensors.single {
            CaptionProto.text(it, 8).single().startsWith("model.shared.weight") &&
                CaptionProto.text(it, 8).single().endsWith("_quantized")
        }
        weight = CaptionProto.text(tensor, 8).single()
        scale = weight.removeSuffix("_quantized") + "_scale"
        zero = weight.removeSuffix("_quantized") + "_zero_point"
        require(tensor.filter { it.number == 1 }.map { integer(it.value) } ==
            listOf(config.vocabulary.toLong(), config.width.toLong()))
        require(integer(tensor.single { it.number == 2 }.value) == 2L)
        require(listOf(scale, zero).all { name -> tensors.any { CaptionProto.text(it, 8) == listOf(name) } })
        val data = tensor.filter { it.number == 13 }.associate {
            val entry = CaptionProto.fields(it.value)
            CaptionProto.text(entry, 1).single() to CaptionProto.text(entry, 2).single()
        }
        require(data["location"] == weights.name)
        val offset = checkNotNull(data["offset"]).toLong()
        val size = config.vocabulary.toLong() * config.width
        require(data["length"]?.toLong() == size && offset >= 0 && offset <= weights.length() - size)
        val initializers = mutableListOf<ByteArray>()
        RandomAccessFile(weights, "rw").use { file ->
            val input = ByteArray(block * config.width)
            val output = ByteArray(input.size)
            for ((index, start) in (0 until config.vocabulary step block).withIndex()) {
                currentCoroutineContext().ensureActive()
                val columns = minOf(block, config.vocabulary - start)
                val length = columns * config.width
                file.seek(offset + start.toLong() * config.width); file.readFully(input, 0, length)
                for (row in 0 until columns) {
                    if (row % 128 == 0) currentCoroutineContext().ensureActive()
                    for (column in 0 until config.width) output[column * columns + row] = input[row * config.width + column]
                }
                file.seek(file.length())
                file.write(ByteArray(((16384 - file.length() % 16384) % 16384).toInt()))
                val at = file.filePointer
                file.write(output, 0, length)
                initializers += CaptionProto.message(5, external("$prefix$index.q", columns, at, length, weights.name))
            }
        }
        var projections = 0
        fun rewriteGraph(bytes: ByteArray): ByteArray {
            var removed = 0
            var replaced = 0
            val fields = CaptionProto.fields(bytes).flatMap { field ->
                if (field.number != 1) listOf(field.bytes) else {
                    val node = CaptionProto.fields(field.value)
                    val op = CaptionProto.text(node, 4).single()
                    val inputs = CaptionProto.text(node, 1)
                    val outputs = CaptionProto.text(node, 2)
                    when {
                        op == "If" -> listOf(CaptionProto.message(1, CaptionProto.join(node.map { attr ->
                            if (attr.number != 5) attr.bytes else {
                                val a = CaptionProto.fields(attr.value)
                                CaptionProto.message(5, CaptionProto.join(a.map {
                                    if (it.number == 6) CaptionProto.message(6, rewriteGraph(it.value)) else it.bytes
                                }))
                            }
                        })))
                        op == "Transpose" && outputs == listOf(matrix) -> {
                            require(inputs == listOf(weight) && node.none { it.number == 5 }); removed++; emptyList()
                        }
                        op == "DequantizeLinear" && outputs == listOf(floatMatrix) -> {
                            require(inputs == listOf(matrix, scale, zero)); removed++; emptyList()
                        }
                        op == "MatMul" && inputs.getOrNull(1) == floatMatrix -> {
                            require(inputs.size == 2 && outputs.size == 1); replaced++; projections++
                            blockNodes(inputs[0], outputs.single()).map { CaptionProto.message(1, it) }
                        }
                        else -> listOf(field.bytes)
                    }
                }
            }
            require((removed == 0 && replaced == 0) || (removed == 2 && replaced == 1))
            return CaptionProto.join(fields)
        }
        val rewritten = rewriteGraph(root.single { it.number == 7 }.value)
        require(projections in 1..2) { "Unexpected pinned Marian head" }
        currentCoroutineContext().ensureActive()
        model.writeBytes(CaptionProto.join(root.map {
            if (it.number == 7) CaptionProto.message(7, rewritten + CaptionProto.join(initializers)) else it.bytes
        }) + CaptionProto.message(14, CaptionProto.string(1, "mn_streamx_language_projection") +
            CaptionProto.string(2, "MN StreamX v30: streamed transposed uint8 head blocks; original scores, vocabulary and weights retained.")))
    }

    private fun blockNodes(hidden: String, output: String): List<ByteArray> = buildList {
        val scores = mutableListOf<String>()
        for ((index, _) in (0 until config.vocabulary step block).withIndex()) {
            val name = "$prefix$index."
            add(node("DequantizeLinear", listOf(name + "q", scale, zero), name + "float"))
            add(node("MatMul", listOf(hidden, name + "float"), name + "scores"))
            scores += name + "scores"
        }
        val axis = CaptionProto.string(1, "axis") + CaptionProto.integer(3, 2) + CaptionProto.integer(20, 2)
        add(node("Concat", scores, output, CaptionProto.message(5, axis)))
    }
    private fun external(name: String, columns: Int, offset: Long, length: Int, location: String): ByteArray {
        val entries = listOf("location" to location, "offset" to offset.toString(), "length" to length.toString())
            .map { (key, value) -> CaptionProto.message(13, CaptionProto.string(1, key) + CaptionProto.string(2, value)) }
        return CaptionProto.integer(1, config.width.toLong()) + CaptionProto.integer(1, columns.toLong()) +
            CaptionProto.integer(2, 2) + CaptionProto.string(8, name) + CaptionProto.join(entries) + CaptionProto.integer(14, 1)
    }
    private fun node(op: String, inputs: List<String>, output: String, attributes: ByteArray = byteArrayOf()) =
        CaptionProto.join(inputs.map { CaptionProto.string(1, it) }) + CaptionProto.string(2, output) +
            CaptionProto.string(3, "mnx.language.$output") + CaptionProto.string(4, op) + attributes
    private fun integer(bytes: ByteArray): Long {
        var value = 0L
        for ((index, byte) in bytes.withIndex()) {
            require(index < 9)
            val part = byte.toInt() and 255
            value = value or ((part and 127).toLong() shl (index * 7))
            if (part < 128) { require(index == bytes.lastIndex); return value }
        }
        error("Truncated initializer")
    }
}