package mnstudio.apps.mnstreamx.data.caption

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptionReadyModelCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val identity = "a".repeat(64)
    private fun source() = temporary.newFile("decoder.onnx").apply { writeText("verified downloaded model") }
    private fun build(source: File): File {
        val intermediate = File(source.parentFile, "decoder-mnx-mapped-test").apply { mkdirs() }
        File(intermediate, "weights.bin").writeBytes(ByteArray(512))
        val ready = File(intermediate, "optimized-test").apply { mkdirs() }
        File(ready, "weights.bin").writeBytes(ByteArray(64) { 7 })
        return File(ready, "model.onnx").apply { writeText("compiled graph") }
    }
    @Test fun finalCacheSkipsIntermediateRebuildAndReclaimsItsDuplicateWeights() = runTest {
        val source = source(); var builds = 0
        val cache = CaptionReadyModelCache { "runtime-a" }
        val first = cache.prepare(source, identity) { builds++; build(source) }
        assertTrue(first.isFile); assertFalse(File(source.parentFile, "decoder-mnx-mapped-test").exists())
        assertEquals("verified downloaded model", source.readText())
        val next = CaptionReadyModelCache { "runtime-a" }.prepare(source, identity) { builds++; build(source) }
        assertEquals(first, next); assertEquals(1, builds)
    }
    @Test fun corruptedFinalWeightsAreRebuiltEvenWhenTheirSizeIsUnchanged() = runTest {
        val source = source(); var builds = 0
        val cache = CaptionReadyModelCache { "runtime-a" }
        val first = cache.prepare(source, identity) { builds++; build(source) }
        File(first.parentFile, "weights.bin").writeBytes(ByteArray(64) { 9 })
        cache.prepare(source, identity) { builds++; build(source) }
        assertEquals(2, builds)
        assertArrayEquals(ByteArray(64) { 7 }, File(first.parentFile, "weights.bin").readBytes())
    }
    @Test fun failedNewPreparationPreservesThePreviousRuntimeCacheAndUnrelatedFiles() = runTest {
        val source = source()
        val first = CaptionReadyModelCache { "runtime-a" }.prepare(source, identity) { build(source) }
        val unrelated = temporary.newFile("notes.txt").apply { writeText("keep") }
        try {
            CaptionReadyModelCache { "runtime-b" }.prepare(source, identity) { throw CancellationException() }
            fail("Cancellation hidden")
        } catch (_: CancellationException) { }
        assertTrue(first.isFile); assertEquals("keep", unrelated.readText())
        val next = CaptionReadyModelCache { "runtime-b" }.prepare(source, identity) { build(source) }
        assertTrue(next.isFile); assertFalse(first.exists()); assertTrue(unrelated.isFile)
    }
}