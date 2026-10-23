package com.yourapp.iptv.data.caption

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarianLanguageProjectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val weight = "model.shared.weight_quantized"
    private fun node(op: String, inputs: List<String>, output: String) = CaptionProto.message(1,
        CaptionProto.join(inputs.map { CaptionProto.string(1, it) }) + CaptionProto.string(2, output) + CaptionProto.string(4, op))
    private fun source() = temporary.newFile("source.onnx").apply {
        val tensor = CaptionProto.integer(1, 1000) + CaptionProto.integer(1, 128) + CaptionProto.integer(2, 2) +
            CaptionProto.string(8, weight) + CaptionProto.message(9, ByteArray(128000) { (it % 251).toByte() })
        val graph = CaptionProto.message(5, tensor) +
            CaptionProto.message(5, CaptionProto.string(8, "model.shared.weight_scale")) +
            CaptionProto.message(5, CaptionProto.string(8, "model.shared.weight_zero_point")) +
            node("Transpose", listOf(weight), "model.shared.weight_transposed_quantized") +
            node("DequantizeLinear", listOf("model.shared.weight_transposed_quantized", "model.shared.weight_scale",
                "model.shared.weight_zero_point"), "model.shared.weight_transposed") +
            node("MatMul", listOf("hidden", "model.shared.weight_transposed"), "logits")
        writeBytes(CaptionProto.message(7, graph))
    }
    @Test fun modelSpecificDimensionsAndAllQuantizedBytesSurviveTheStreamedTranspose() = runTest {
        val model = temporary.newFile("model.onnx"); val weights = temporary.newFile("weights.bin")
        MarianLanguageProjection(MarianModelConfig(1000, 128, 4, 999, 0, 999)).export(source(), model, weights)
        val root = CaptionProto.fields(model.readBytes())
        val graph = CaptionProto.fields(root.single { it.number == 7 }.value)
        val nodes = graph.filter { it.number == 1 }.map { CaptionProto.fields(it.value) }
        assertEquals(listOf("DequantizeLinear", "MatMul", "Concat"), nodes.map { CaptionProto.text(it, 4).single() })
        val tensor = graph.filter { it.number == 5 }.map { CaptionProto.fields(it.value) }
            .single { CaptionProto.text(it, 8) == listOf("mnx.language.head.0.q") }
        val metadata = tensor.filter { it.number == 13 }.associate {
            val entry = CaptionProto.fields(it.value); CaptionProto.text(entry, 1).single() to CaptionProto.text(entry, 2).single()
        }
        val at = metadata.getValue("offset").toInt()
        assertEquals(0, at % 16384)
        val bytes = weights.readBytes()
        for (row in 0 until 1000) for (column in 0 until 128)
            assertEquals(((row * 128 + column) % 251).toByte(), bytes[at + column * 1000 + row])
    }
    @Test fun aPackWithIncompatibleDimensionsCannotReuseAnotherModelsProjection() = runTest {
        try {
            MarianLanguageProjection(MarianModelConfig(1001, 128, 4, 1000, 0, 1000))
                .export(source(), temporary.newFile("model.onnx"), temporary.newFile("weights.bin"))
            fail("Incompatible dimensions accepted")
        } catch (_: IllegalArgumentException) { }
    }
}