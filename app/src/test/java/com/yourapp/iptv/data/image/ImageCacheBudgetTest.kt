package com.yourapp.iptv.data.image

import org.junit.Assert.*
import org.junit.Test

class ImageCacheBudgetTest {
    @Test fun tinyAndInvalidHeapReportsCannotAllocateAnOversizedCache() {
        for (heap in listOf(Int.MIN_VALUE, -1, 0, 16, 32, 64, 128, 256, Int.MAX_VALUE)) {
            for (lowRam in listOf(false, true)) {
                val budget = ImageCacheBudget.forDevice(heap, lowRam)
                val reportedHeap = heap.coerceAtLeast(16).toLong() * 1024 * 1024
                assertTrue(budget.memoryBytes > 0)
                assertTrue(budget.memoryBytes <= reportedHeap / 8)
                assertTrue(budget.memoryBytes <= 32 * 1024 * 1024)
                assertTrue(budget.diskBytes in 1..96L * 1024 * 1024)
                assertTrue(budget.maxRequestsPerHost in 1..budget.maxRequests)
                assertTrue(budget.maxDecodes in 1..budget.maxRequestsPerHost)
            }
        }
    }
    @Test fun lowRamDevicesReduceCacheAndDecodePressure() {
        for (heap in listOf(64, 128, 256, 512)) {
            val ordinary = ImageCacheBudget.forDevice(heap, false)
            val low = ImageCacheBudget.forDevice(heap, true)
            assertTrue(low.memoryBytes <= ordinary.memoryBytes)
            assertTrue(low.diskBytes < ordinary.diskBytes)
            assertTrue(low.maxRequests < ordinary.maxRequests)
            assertTrue(low.maxDecodes < ordinary.maxDecodes)
        }
    }
}