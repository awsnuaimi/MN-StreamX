package com.yourapp.iptv.data.caption

import com.yourapp.iptv.domain.caption.CaptionStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OnnxOptimizedModelCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val identity = "a".repeat(64)
    private fun source(): File {
        File(temporary.root, "weights.bin").writeBytes(ByteArray(128) { 9 })
        return temporary.newFile("model.onnx").apply { writeBytes(ByteArray(64) { 2 }) }
    }
    private fun write(output: File) {
        output.writeBytes(ByteArray(32) { 5 })
        File(output.parentFile, "weights.bin").writeBytes(ByteArray(96) { 7 })
    }
    private fun cache(compile: suspend (File, File) -> Unit, runtime: String = "ort120-armv7-cse-off") =
        OnnxOptimizedModelCache(compile, runtimeSignature = { runtime }, freeBytes = { Long.MAX_VALUE })

    @Test fun checkedCompiledGraphIsReusedAcrossCacheInstances() = runTest {
        val source = source(); var calls = 0
        val compile: suspend (File, File) -> Unit = { _, output -> calls++; write(output) }
        val first = cache(compile).prepare(source, identity)
        assertEquals(first, cache(compile).prepare(source, identity))
        assertEquals(1, calls)
        assertArrayEquals(ByteArray(32) { 5 }, first.readBytes())
        assertArrayEquals(ByteArray(128) { 9 }, File(source.parentFile, "weights.bin").readBytes())
    }
    @Test fun engineArchitectureOrPreparationPolicyChangeCannotReuseTheOldGraph() = runTest {
        val source = source(); var calls = 0
        val compile: suspend (File, File) -> Unit = { _, output -> calls++; write(output) }
        val first = cache(compile).prepare(source, identity)
        val upgraded = cache(compile, "ort121-armv7-cse-off").prepare(source, identity)
        val otherAbi = cache(compile, "ort120-aarch64-cse-off").prepare(source, identity)
        assertEquals(3, calls)
        assertEquals(3, setOf(first.parentFile, upgraded.parentFile, otherAbi.parentFile).size)
        assertTrue(first.exists())
    }
    @Test fun sourceModelIdentityAndGraphContentBothParticipateInCacheIdentity() = runTest {
        val source = source()
        val compiler = cache({ _, output -> write(output) })
        val original = compiler.prepare(source, identity)
        val otherModel = compiler.prepare(source, "b".repeat(64))
        source.writeBytes(ByteArray(64) { 3 })
        val changedGraph = compiler.prepare(source, identity)
        assertEquals(3, setOf(original.parentFile, otherModel.parentFile, changedGraph.parentFile).size)
    }
    @Test fun corruptWeightsAreCheckedAndRecompiledBeforeMapping() = runTest {
        val source = source(); var calls = 0
        val compiler = cache({ _, output -> calls++; write(output) })
        val model = compiler.prepare(source, identity)
        val data = File(model.parentFile, "weights.bin")
        data.writeBytes(ByteArray(96) { 8 }); assertTrue(data.setLastModified(data.lastModified() + 2000))
        assertEquals(model, compiler.prepare(source, identity))
        assertEquals(2, calls)
        assertArrayEquals(ByteArray(96) { 7 }, data.readBytes())
    }
    @Test fun truncatedGraphIsRecompiled() = runTest {
        val source = source(); var calls = 0
        val compiler = cache({ _, output -> calls++; write(output) })
        val model = compiler.prepare(source, identity)
        model.writeBytes(byteArrayOf(1))
        compiler.prepare(source, identity)
        assertEquals(2, calls)
        assertArrayEquals(ByteArray(32) { 5 }, model.readBytes())
    }
    @Test fun failedCompileDoesNotReplaceAnExistingCacheOrLeavePartialDirectories() = runTest {
        val source = source()
        val published = cache({ _, output -> write(output) }).prepare(source, identity)
        val broken = cache({ _, output -> output.writeText("partial"); error("Interrupted preparation") }, "ort-new")
        try { broken.prepare(source, identity); fail("Compiler failure hidden") }
        catch (_: IllegalStateException) { }
        assertArrayEquals(ByteArray(32) { 5 }, published.readBytes())
        assertTrue(temporary.root.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }
    @Test fun cancellationNeverPublishesTheIncompleteCompile() = runTest {
        val source = source()
        val compiler = cache({ _, output -> output.writeText("partial"); throw CancellationException() })
        try { compiler.prepare(source, identity); fail("Cancellation hidden") }
        catch (_: CancellationException) { }
        assertEquals(setOf("model.onnx", "weights.bin"), temporary.root.listFiles().orEmpty().map { it.name }.toSet())
    }
    @Test fun absentExternalWeightsRejectTheCompileResult() = runTest {
        val source = source()
        val compiler = cache({ _, output -> output.writeBytes(ByteArray(12)) })
        try { compiler.prepare(source, identity); fail("Missing weights accepted") }
        catch (_: IllegalStateException) { }
        assertTrue(temporary.root.listFiles().orEmpty().none { it.isDirectory })
    }
    @Test fun storageReserveIsCheckedBeforeOpeningAnyNativeSession() = runTest {
        val compiler = OnnxOptimizedModelCache({ _, _ -> error("Must not invoke native compilation") },
            runtimeSignature = { "test" }, freeBytes = { 0L })
        try { compiler.prepare(source(), identity); fail("Low storage ignored") }
        catch (error: CaptionStorageException) {
            assertEquals(0L, error.availableBytes)
            assertTrue(error.requiredBytes > 48 * 1048576L)
        }
    }
    @Test fun concurrentRequestsDoNotOpenTwoNativeCompilersForOneCache() = runTest {
        val source = source(); var calls = 0
        val compiler = cache({ _, output -> calls++; yield(); write(output) })
        val result = List(2) { async { compiler.prepare(source, identity) } }.awaitAll()
        assertEquals(result[0], result[1])
        assertEquals(1, calls)
    }
}