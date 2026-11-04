package com.yourapp.iptv.data.image

import android.content.ContextWrapper
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.yourapp.iptv.IptvApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = IptvApplication::class)
@OptIn(ExperimentalCoilApi::class)
class ImageLoaderFactoryTest {
    @Test fun applicationAndDestinationContextsResolveOneBudgetedLoader() {
        val app = RuntimeEnvironment.getApplication()
        val first = Coil.imageLoader(app)
        try {
            val destination = ContextWrapper(app)
            assertSame(first, Coil.imageLoader(destination))
            assertSame(first, Coil.imageLoader(app))
            val manager = checkNotNull(app.getSystemService(android.app.ActivityManager::class.java))
            val budget = ImageCacheBudget.forDevice(manager.memoryClass, manager.isLowRamDevice)
            assertEquals(budget.memoryBytes, first.memoryCache!!.maxSize)
            assertEquals(budget.diskBytes, first.diskCache!!.maxSize)
            assertEquals(app.cacheDir.resolve("image_cache").absolutePath, first.diskCache!!.directory.toFile().absolutePath)
        } finally { first.shutdown() }
    }
}