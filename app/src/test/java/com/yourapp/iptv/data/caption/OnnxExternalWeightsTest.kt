package com.yourapp.iptv.data.caption

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnnxExternalWeightsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun tensor(name: String, raw: ByteArray) = CaptionProto.integer(1, raw.size.toLong()) +
        CaptionProto.integer(2, 2) + CaptionProto.string(8, name) + CaptionProto.message(9, raw)
    @Test fun weightsRemainByteExactAndMetadataAndSmallConstantsArePreserved() = runTest {
        val large = ByteArray(70000) { (it % 251).toByte() }; val small = ByteArray(8) { 3 }
        val input = temporary.newFile("source.onnx")
        val graph = CaptionProto.message(5, tensor("large", large)) + CaptionProto.message(5, tensor("small", small))
        input.writeBytes(CaptionProto.string(2, "provider") + CaptionProto.message(7, graph))
        val model = temporary.newFile("model.onnx"); val weights = temporary.newFile("weights.bin")
        OnnxExternalWeights.export(input, model, weights)
        assertArrayEquals(large, weights.readBytes())
        val modelFields = CaptionProto.fields(model.readBytes())
        assertEquals("provider", CaptionProto.text(modelFields, 2).single())
        val tensors = CaptionProto.fields(modelFields.single { it.number == 7 }.value).filter { it.number == 5 }
            .map { CaptionProto.fields(it.value) }
        val moved = tensors.first(); val original = tensors.last()
        assertTrue(moved.none { it.number == 9 }); assertEquals("large", CaptionProto.text(moved, 8).single())
        val metadata = moved.filter { it.number == 13 }.associate {
            val row = CaptionProto.fields(it.value); CaptionProto.text(row, 1).single() to CaptionProto.text(row, 2).single()
        }
        assertEquals(mapOf("location" to "weights.bin", "offset" to "0", "length" to "70000"), metadata)
        assertArrayEquals(small, original.single { it.number == 9 }.value)
    }
    @Test fun multipleLargeWeightsAreAlignedForBothFourAndSixteenKilobytePages() = runTest {
        val input = temporary.newFile("source.onnx")
        input.writeBytes(CaptionProto.message(7, CaptionProto.message(5, tensor("first", ByteArray(70000) { 1 })) +
            CaptionProto.message(5, tensor("second", ByteArray(70000) { 2 }))))
        val model = temporary.newFile("model.onnx"); val weights = temporary.newFile("weights.bin")
        OnnxExternalWeights.export(input, model, weights)
        val graph = CaptionProto.fields(model.readBytes()).single { it.number == 7 }
        val rows = CaptionProto.fields(graph.value).filter { it.number == 5 }.map { CaptionProto.fields(it.value) }
        val offsets = rows.map { row -> row.filter { it.number == 13 }.map { CaptionProto.fields(it.value) }
            .single { CaptionProto.text(it, 1) == listOf("offset") }.let { CaptionProto.text(it, 2).single().toInt() } }
        assertEquals(listOf(0, 81920), offsets)
        assertArrayEquals(ByteArray(70000) { 2 }, weights.readBytes().copyOfRange(81920, 151920))
    }
    @Test fun invalidLengthsAreRejectedBeforePublishingAnExternalModel() = runTest {
        val input = temporary.newFile("broken.onnx").apply { writeBytes(byteArrayOf(58, 99, 1)) }
        try { OnnxExternalWeights.export(input, temporary.newFile(), temporary.newFile()); fail("Truncation ignored") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun explicitlyEmbeddedWeightsReplaceTheirDefaultLocationRatherThanKeepingTwoLocationFields() = runTest {
        val input = temporary.newFile("explicit.onnx")
        input.writeBytes(CaptionProto.message(7, CaptionProto.message(5,
            tensor("weight", ByteArray(70000) { 7 }) + CaptionProto.integer(14, 0))))
        val model = temporary.newFile("mapped.onnx"); val weights = temporary.newFile("data.bin")
        OnnxExternalWeights.export(input, model, weights)
        val graph = CaptionProto.fields(model.readBytes()).single { it.number == 7 }
        val moved = CaptionProto.fields(CaptionProto.fields(graph.value).single { it.number == 5 }.value)
        assertEquals(1, moved.count { it.number == 14 })
        assertArrayEquals(byteArrayOf(1), moved.single { it.number == 14 }.value)
        assertArrayEquals(ByteArray(70000) { 7 }, weights.readBytes())
    }
    @Test fun existingExternalLocationsAreRejectedEvenWhenTheirRawDataIsPresent() = runTest {
        val input = temporary.newFile("external.onnx")
        input.writeBytes(CaptionProto.message(7, CaptionProto.message(5,
            tensor("weight", ByteArray(70000)) + CaptionProto.integer(14, 1))))
        try { OnnxExternalWeights.export(input, temporary.newFile(), temporary.newFile()); fail("External input accepted") }
        catch (_: IllegalArgumentException) { }
    }
}