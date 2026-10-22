package com.yourapp.iptv.data.caption

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MarianPreparedProjectionTest {
    private val prefix = "mnx.blockhead.v1."
    private fun node(op: String, inputs: List<String>, output: String) =
        CaptionProto.join(inputs.map { CaptionProto.string(1, it) }) +
            CaptionProto.string(2, output) + CaptionProto.string(4, op)
    private fun model(transpose: Boolean = true): ByteArray {
        val matrix = "model.shared.weight_transposed_quantized"
        val unblocked = (if (transpose) CaptionProto.message(1, node("Transpose",
            listOf("model.shared.weight_merged_0_quantized"), matrix)) else byteArrayOf()) +
            CaptionProto.message(1, node("DequantizeLinear", listOf(matrix,
                "model.shared.weight_merged_0_scale", "model.shared.weight_merged_0_zero_point"),
                "model.shared.weight_transposed")) +
            CaptionProto.message(1, node("MatMul", listOf("hidden", "model.shared.weight_transposed"), "logits"))
        fun branch(name: String) = CaptionProto.message(5, CaptionProto.string(1, name) +
            CaptionProto.message(6, unblocked))
        val conditional = checkNotNull(MarianBlockGraph.rewrite(CaptionProto.string(4, "If") +
            branch("then_branch") + branch("else_branch")))
        val graph = CaptionProto.message(1, conditional) + CaptionProto.string(2, "decoder") +
            CaptionProto.message(12, CaptionProto.string(1, "logits")) +
            CaptionProto.message(12, CaptionProto.string(1, "present.0.decoder.key")) +
            CaptionProto.message(5, CaptionProto.string(8, "original-weight"))
        return CaptionProto.string(2, "provider") + CaptionProto.message(7, graph)
    }
    private fun blocks() = (0 until 16).map { CaptionProto.string(8, "$prefix$it.q") }

    @Test fun preparedGraphRemovesRepeatedWeightTransformsAndPreservesCachesAndOriginalInitializers() {
        val transformed = MarianPreparedProjection.rewrite(model(), blocks())
        val root = CaptionProto.fields(transformed)
        assertEquals(listOf("provider"), CaptionProto.text(root, 2))
        val graph = CaptionProto.fields(root.single { it.number == 7 }.value)
        val outputs = graph.filter { it.number == 12 }.flatMap { CaptionProto.text(CaptionProto.fields(it.value), 1) }
        assertEquals(listOf(MarianPreparedProjection.TOKEN_OUTPUT, "present.0.decoder.key"), outputs)
        assertEquals(17, graph.count { it.number == 5 })
        val conditional = CaptionProto.fields(graph.filter { it.number == 1 }.first().value)
        for (attribute in conditional.filter { it.number == 5 }) {
            val fields = CaptionProto.fields(attribute.value)
            val branch = CaptionProto.fields(fields.single { it.number == 6 }.value)
            val nodes = branch.filter { it.number == 1 }.map { CaptionProto.fields(it.value) }
            assertTrue(nodes.none { CaptionProto.text(it, 4).single() in setOf("Transpose", "Slice", "Constant") })
            assertEquals(16, nodes.count { CaptionProto.text(it, 4) == listOf("DequantizeLinear") })
            assertEquals(16, nodes.count { CaptionProto.text(it, 4) == listOf("MatMul") })
        }
        val nodes = graph.filter { it.number == 1 }.map { CaptionProto.fields(it.value) }
        assertEquals(listOf("ArgMax"), CaptionProto.text(nodes.last(), 4))
        assertEquals(listOf(MarianPreparedProjection.TOKEN_OUTPUT), CaptionProto.text(nodes.last(), 2))
    }

    @Test fun aDifferentPinnedLayoutIsRejected() {
        try { MarianPreparedProjection.rewrite(model(transpose = false), blocks()); fail("Missing transpose accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun allQuantizedWeightBytesSurviveTheTransposeExactly() = runTest {
        val input = byteArrayOf(0, 127, -128, -1, 10, 20)
        assertArrayEquals(byteArrayOf(0, -1, 127, 10, -128, 20),
            MarianPreparedProjection.transpose(input, 2, 3))
        assertArrayEquals(input, MarianPreparedProjection.transpose(
            MarianPreparedProjection.transpose(input, 2, 3), 3, 2))
    }

    @Test fun transposeHonorsCoroutineCancellation() = runTest {
        val job = kotlinx.coroutines.Job().apply { cancel() }
        try {
            kotlinx.coroutines.withContext(currentCoroutineContext() + job) {
                MarianPreparedProjection.transpose(ByteArray(4096), 64, 64)
            }
            fail("Cancellation ignored")
        } catch (_: CancellationException) { }
    }
}