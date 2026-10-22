package com.yourapp.iptv.data.caption

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OnnxExternalAlignmentTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun tensor(name: String, offset: Long, length: Long, location: String = "weights.bin"): ByteArray {
        val entries = listOf("location" to location, "offset" to offset.toString(), "length" to length.toString())
        return CaptionProto.integer(1, length) + CaptionProto.integer(2, 2) + CaptionProto.string(8, name) +
            CaptionProto.integer(14, 1) + CaptionProto.join(entries.map { (key, value) ->
                CaptionProto.message(13, CaptionProto.string(1, key) + CaptionProto.string(2, value)) })
    }
    private fun file(graph: ByteArray): File {
        File(temporary.root, "weights.bin").writeBytes(ByteArray(10) { it.toByte() })
        return temporary.newFile("model.onnx").apply {
            writeBytes(CaptionProto.string(2, "provider") + CaptionProto.message(7, graph)) }
    }
    private fun graph(model: File) = CaptionProto.fields(CaptionProto.fields(model.readBytes()).single { it.number == 7 }.value)
    private fun metadata(tensor: ByteArray) = CaptionProto.fields(tensor).filter { it.number == 13 }.associate {
        val entry = CaptionProto.fields(it.value)
        CaptionProto.text(entry, 1).single() to CaptionProto.text(entry, 2).single()
    }

    @Test fun unalignedRangesBecomePageAlignedWithoutChangingBytesOrDuplicatingAliases() = runTest {
        val branch = CaptionProto.message(5, tensor("nested", 5, 4))
        val attribute = CaptionProto.string(1, "then_branch") + CaptionProto.message(6, branch)
        val node = CaptionProto.string(4, "If") + CaptionProto.message(5, attribute)
        val model = file(CaptionProto.message(5, tensor("first", 1, 3)) +
            CaptionProto.message(5, tensor("alias", 1, 3)) + CaptionProto.message(1, node))
        OnnxExternalAlignment.prepare(model)
        val top = graph(model)
        val first = top.filter { it.number == 5 }.map { metadata(it.value) }
        assertEquals(listOf("0", "0"), first.map { it["offset"] })
        val nodes = CaptionProto.fields(top.single { it.number == 1 }.value)
        val attributes = CaptionProto.fields(nodes.single { it.number == 5 }.value)
        val nested = CaptionProto.fields(attributes.single { it.number == 6 }.value)
        assertEquals("16384", metadata(nested.single { it.number == 5 }.value)["offset"])
        val data = File(temporary.root, "weights.bin").readBytes()
        assertEquals(16388, data.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), data.copyOfRange(0, 3))
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), data.copyOfRange(16384, 16388))
        assertEquals(listOf("provider"), CaptionProto.text(CaptionProto.fields(model.readBytes()), 2))
        assertFalse(File(temporary.root, "weights-aligned.part").exists())
    }
    @Test fun inlineTensorAndUnrelatedFieldsAreRetainedExactly() = runTest {
        val inline = CaptionProto.integer(1, 1) + CaptionProto.integer(2, 7) + CaptionProto.integer(7, 42)
        val model = file(CaptionProto.message(5, tensor("external", 1, 3)) +
            CaptionProto.message(5, inline) + CaptionProto.string(10, "original documentation"))
        OnnxExternalAlignment.prepare(model)
        assertArrayEquals(inline, graph(model).filter { it.number == 5 }.last().value)
        assertEquals(listOf("original documentation"), CaptionProto.text(graph(model), 10))
    }
    @Test fun invalidRangeDoesNotModifyTheOriginalWeightsOrGraph() = runTest {
        val model = file(CaptionProto.message(5, tensor("broken", 8, 4)))
        val before = model.readBytes()
        try { OnnxExternalAlignment.prepare(model); fail("Out-of-range weights accepted") }
        catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, model.readBytes())
        assertArrayEquals(ByteArray(10) { it.toByte() }, File(temporary.root, "weights.bin").readBytes())
        assertFalse(File(temporary.root, "weights-aligned.part").exists())
    }
    @Test fun unexpectedExternalLocationIsRejectedBeforeAnySourceFileIsChanged() = runTest {
        val model = file(CaptionProto.message(5, tensor("broken", 1, 3, "../other.bin")))
        val before = model.readBytes()
        try { OnnxExternalAlignment.prepare(model); fail("Unexpected external location accepted") }
        catch (_: IllegalArgumentException) { }
        assertArrayEquals(before, model.readBytes())
        assertArrayEquals(ByteArray(10) { it.toByte() }, File(temporary.root, "weights.bin").readBytes())
    }
}