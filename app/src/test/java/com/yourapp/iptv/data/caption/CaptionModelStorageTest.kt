package com.yourapp.iptv.data.caption

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CaptionModelStorageTest {
    @Test fun onlyDedicatedModelDirectoryIsRemoved() {
        val root = Files.createTempDirectory("mnx-storage-test").toFile()
        try {
            val models = root.resolve("caption-models")
            val model = models.resolve("marian-en-de-v30/onnx/decoder.onnx")
            assertTrue(model.parentFile.mkdirs())
            model.writeBytes(ByteArray(125) { 4 })
            val account = root.resolve("user-account.xml")
            account.writeText("keep")
            val catalogue = root.resolve("home_catalog_v24.bin")
            catalogue.writeText("channels")
            val cache = CaptionModelStorage(models)
            assertEquals(125L, cache.bytes())
            assertEquals(125L, cache.clear())
            assertEquals(0L, cache.bytes())
            assertTrue(account.isFile)
            assertEquals("keep", account.readText())
            assertTrue(catalogue.isFile)
            assertEquals(0L, cache.clear())
        } finally { root.deleteRecursively() }
    }
}