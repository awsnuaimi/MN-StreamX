package com.yourapp.iptv.data.caption

/** Same float head and vocabulary, evaluated 4096 columns at a time instead of a 125 MiB matrix. */
internal object MarianBlockGraph {
    private const val MATRIX = "model.shared.weight_transposed_quantized"
    private const val FLOAT_MATRIX = "model.shared.weight_transposed"
    private const val SCALE = "model.shared.weight_merged_0_scale"
    private const val ZERO = "model.shared.weight_merged_0_zero_point"
    private const val PREFIX = "mnx.blockhead.v1."

    fun rewrite(node: ByteArray): ByteArray? {
        val rows = CaptionProto.fields(node)
        if (CaptionProto.text(rows, 4).singleOrNull() != "If") return null
        var branches = 0
        val changed = rows.map { field ->
            if (field.number != 5) field.bytes else {
                val attributes = CaptionProto.fields(field.value)
                val name = CaptionProto.text(attributes, 1).singleOrNull()
                if (name !in setOf("then_branch", "else_branch")) field.bytes else {
                    val rewritten = attributes.map { attribute ->
                        if (attribute.number != 6) attribute.bytes else {
                            branches++; CaptionProto.message(6, graph(attribute.value))
                        }
                    }
                    CaptionProto.message(5, CaptionProto.join(rewritten))
                }
            }
        }
        require(branches == 2) { "Unexpected Marian branches" }
        return CaptionProto.join(changed)
    }
    private fun graph(bytes: ByteArray): ByteArray {
        var removed = 0; var replaced = 0
        val result = CaptionProto.fields(bytes).flatMap { field ->
            if (field.number != 1) listOf(field.bytes) else {
                val node = CaptionProto.fields(field.value)
                val op = CaptionProto.text(node, 4).singleOrNull()
                val inputs = CaptionProto.text(node, 1); val outputs = CaptionProto.text(node, 2)
                when {
                    op == "DequantizeLinear" && outputs == listOf(FLOAT_MATRIX) -> {
                        require(inputs == listOf(MATRIX, SCALE, ZERO)); removed++; emptyList()
                    }
                    op == "MatMul" && inputs.getOrNull(1) == FLOAT_MATRIX -> {
                        require(inputs.size == 2 && outputs.size == 1); replaced++
                        blockNodes(inputs.first(), outputs.single()).map { CaptionProto.message(1, it) }
                    }
                    else -> listOf(field.bytes)
                }
            }
        }
        require(removed == 1 && replaced == 1) { "Unexpected Marian projection" }
        return CaptionProto.join(result)
    }
    private fun blockNodes(hidden: String, output: String): List<ByteArray> = buildList {
        add(constant(PREFIX + "axis", 1))
        val logits = mutableListOf<String>()
        for ((index, start) in (0 until 64110 step 4096).withIndex()) {
            val name = "$PREFIX$index."
            add(constant(name + "start", start.toLong()))
            add(constant(name + "end", minOf(64110, start + 4096).toLong()))
            add(node("Slice", listOf(MATRIX, name + "start", name + "end", PREFIX + "axis"), name + "q", name + "slice"))
            add(node("DequantizeLinear", listOf(name + "q", SCALE, ZERO), name + "float", name + "dequant"))
            add(node("MatMul", listOf(hidden, name + "float"), name + "logits", name + "matmul"))
            logits += name + "logits"
        }
        val axis = CaptionProto.string(1, "axis") + CaptionProto.integer(3, 2) + CaptionProto.integer(20, 2)
        add(node("Concat", logits, output, PREFIX + "concat", axis))
    }
    private fun constant(name: String, value: Long): ByteArray {
        val tensor = CaptionProto.integer(1, 1) + CaptionProto.integer(2, 7) + CaptionProto.integer(7, value)
        val attribute = CaptionProto.string(1, "value") + CaptionProto.message(5, tensor) + CaptionProto.integer(20, 4)
        return node("Constant", emptyList(), name, name, attribute)
    }
    private fun node(op: String, inputs: List<String>, output: String, name: String, attribute: ByteArray? = null) =
        CaptionProto.join(inputs.map { CaptionProto.string(1, it) }) + CaptionProto.string(2, output) +
            CaptionProto.string(3, name) + CaptionProto.string(4, op) +
            (attribute?.let { CaptionProto.message(5, it) } ?: byteArrayOf())
}