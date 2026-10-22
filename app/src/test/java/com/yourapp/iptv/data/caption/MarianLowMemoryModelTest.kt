package com.yourapp.iptv.data.caption

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarianLowMemoryModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun node(op: String, inputs: List<String>, output: String): ByteArray =
        CaptionProto.join(inputs.map { CaptionProto.string(1, it) }) + CaptionProto.string(2, output) + CaptionProto.string(4, op)
    private fun graph(): ByteArray {
        val dequant = node("DequantizeLinear", listOf("model.shared.weight_transposed_quantized",
            "model.shared.weight_merged_0_scale", "model.shared.weight_merged_0_zero_point"), "model.shared.weight_transposed")
        val matmul = node("MatMul", listOf("hidden", "model.shared.weight_transposed"), "projection")
        return CaptionProto.message(1, dequant) + CaptionProto.message(1, matmul) + CaptionProto.string(2, "branch")
    }
    private fun branch(name: String) = CaptionProto.message(5, CaptionProto.string(1, name) + CaptionProto.message(6, graph()))
    private fun decoder() = CaptionProto.string(4, "If") + branch("then_branch") + branch("else_branch")
    @Test fun rewriteKeepsModelMetadataAndTensorBytesWhileReplacingBothLargeHeads() = runTest {
        val weights = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val source = temporary.newFile("source.onnx")
        val modelGraph = CaptionProto.message(1, decoder()) + CaptionProto.message(5, weights)
        val metadata = CaptionProto.string(2, "provider")
        source.writeBytes(metadata + CaptionProto.message(7, modelGraph))
        val target = temporary.newFile("compact.onnx")
        MarianLowMemoryModel.transform(source, target)
        val model = CaptionProto.fields(target.readBytes())
        assertEquals("provider", CaptionProto.text(model, 2).single())
        val actualGraph = CaptionProto.fields(model.single { it.number == 7 }.value)
        assertArrayEquals(weights, actualGraph.single { it.number == 5 }.value)
        val conditional = CaptionProto.fields(actualGraph.single { it.number == 1 }.value)
        for (attribute in conditional.filter { it.number == 5 }) {
            val fields = CaptionProto.fields(attribute.value)
            val graph = CaptionProto.fields(fields.single { it.number == 6 }.value)
            val nodes = graph.filter { it.number == 1 }.map { CaptionProto.fields(it.value) }
            assertEquals(16, nodes.count { CaptionProto.text(it, 4) == listOf("MatMul") })
            assertEquals(16, nodes.count { CaptionProto.text(it, 4) == listOf("Slice") })
            assertTrue(nodes.none { "model.shared.weight_transposed" in CaptionProto.text(it, 2) })
            assertEquals("projection", CaptionProto.text(nodes.last(), 2).single())
        }
    }
    @Test fun malformedLengthCannotReadBeyondTheModel() = runTest {
        val source = temporary.newFile("broken.onnx").apply { writeBytes(byteArrayOf(58, 100, 1)) }
        try { MarianLowMemoryModel.transform(source, temporary.newFile()); fail("Truncated graph accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun aDifferentDecoderLayoutFailsInsteadOfPublishingAWrongModel() = runTest {
        val source = temporary.newFile("unknown.onnx").apply {
            writeBytes(CaptionProto.message(7, CaptionProto.message(1, node("Identity", listOf("x"), "y"))))
        }
        try { MarianLowMemoryModel.transform(source, temporary.newFile()); fail("Unrecognized model accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun truncatedAndOverflowingProtobufIntegersAreRejected() {
        for (bytes in listOf(byteArrayOf(10, 20, 1), ByteArray(12) { 255.toByte() })) {
            try { CaptionProto.fields(bytes); fail("Invalid wire input accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}