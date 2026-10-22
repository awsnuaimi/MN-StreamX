package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.caption.CaptionStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OnnxMappedModelCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val sha = "a".repeat(64)
    private fun source() = temporary.newFile("encoder.onnx").apply { writeBytes(ByteArray(128)) }
    private fun write(graph: File, weights: File) { graph.writeBytes(ByteArray(32) { 5 }); weights.writeBytes(ByteArray(64) { 7 }) }
    @Test fun validPreparedModelsAreReusedAcrossCacheInstances() = runTest {
        var calls = 0
        val export: suspend (File, File, File) -> Unit = { _, graph, weights -> calls++; write(graph, weights) }
        val input = source()
        val first = OnnxMappedModelCache(export).prepare(input, sha)
        val second = OnnxMappedModelCache(export).prepare(input, sha)
        assertEquals(first, second); assertEquals(1, calls)
        assertTrue(File(first.parentFile, "checksum.txt").readText().contains(sha))
    }
    @Test fun corruptedWeightsAreRebuiltInsteadOfMapped() = runTest {
        var calls = 0
        val export: suspend (File, File, File) -> Unit = { _, graph, weights -> calls++; write(graph, weights) }
        val input = source(); val cache = OnnxMappedModelCache(export)
        val weights = File(cache.prepare(input, sha).parentFile, "weights.bin")
        weights.writeBytes(ByteArray(64) { 8 }); weights.setLastModified(weights.lastModified() + 2000)
        cache.prepare(input, sha)
        assertEquals(2, calls); assertArrayEquals(ByteArray(64) { 7 }, weights.readBytes())
    }
    @Test fun aDifferentPinnedSourceUsesADifferentCacheDirectory() = runTest {
        var calls = 0
        val cache = OnnxMappedModelCache({ _, graph, weights -> calls++; write(graph, weights) })
        val input = source(); val first = cache.prepare(input, sha); val second = cache.prepare(input, "b".repeat(64))
        assertEquals(2, calls); assertNotEquals(first.parentFile, second.parentFile); assertTrue(first.exists())
    }
    @Test fun failurePreservesPublishedModelsAndRemovesTemporaryDirectories() = runTest {
        val input = source()
        val old = OnnxMappedModelCache({ _, graph, weights -> write(graph, weights) }).prepare(input, sha)
        val broken = OnnxMappedModelCache({ _, graph, _ -> graph.writeBytes(ByteArray(10)); error("Interrupted export") })
        try { broken.prepare(input, "b".repeat(64)); fail("Failure hidden") } catch (_: IllegalStateException) { }
        assertArrayEquals(ByteArray(32) { 5 }, old.readBytes())
        assertTrue(temporary.root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }
    @Test fun cancellationNeverPublishesAPartialDirectory() = runTest {
        val input = source()
        val cache = OnnxMappedModelCache({ _, graph, _ -> graph.writeBytes(ByteArray(10)); throw CancellationException() })
        try { cache.prepare(input, sha); fail("Cancellation hidden") } catch (_: CancellationException) { }
        assertEquals(listOf(input.name), temporary.root.listFiles().orEmpty().map { it.name })
    }
    @Test fun storageCheckPrecedesPreparation() = runTest {
        val cache = OnnxMappedModelCache({ _, _, _ -> error("Preparation must not start") }, freeBytes = { 0 })
        try { cache.prepare(source(), sha); fail("Storage shortage ignored") }
        catch (error: CaptionStorageException) { assertEquals(0L, error.availableBytes); assertTrue(error.requiredBytes > 128) }
    }
    @Test fun preparedProjectionCannotReuseTheOldGraphEvenWhenThePinnedSourceIsTheSame() = runTest {
        var calls = 0
        val export: suspend (File, File, File) -> Unit = { _, graph, weights -> calls++; write(graph, weights) }
        val input = source()
        val original = OnnxMappedModelCache(export).prepare(input, sha)
        val prepared = OnnxMappedModelCache(export, variant = "live-v2").prepare(input, sha)
        assertNotEquals(original.parentFile, prepared.parentFile)
        assertEquals(2, calls)
        assertEquals(prepared, OnnxMappedModelCache(export, variant = "live-v2").prepare(input, sha))
        assertEquals(2, calls)
    }
    @Test fun addedProjectionBytesAreIncludedInTheStorageDecision() = runTest {
        val mib = 1048576L
        val cache = OnnxMappedModelCache({ _, _, _ -> error("Storage check must run first") },
            freeBytes = { 32 * mib }, variant = "live-v2", addedWeightBytes = 32 * mib)
        try { cache.prepare(source(), sha); fail("Additional weights were not budgeted") }
        catch (error: CaptionStorageException) { assertTrue(error.requiredBytes > 56 * mib) }
    }
}