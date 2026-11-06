package mnstudio.apps.mnstreamx.data.image

/** Cache limits only: visible bitmaps, the player, and other app memory are separate. */
internal data class ImageCacheBudget(
    val memoryBytes: Int,
    val diskBytes: Long,
    val maxRequests: Int,
    val maxRequestsPerHost: Int,
    val maxDecodes: Int
) {
    companion object {
        private const val MIB = 1024 * 1024

        fun forDevice(memoryClassMb: Int, lowRam: Boolean): ImageCacheBudget {
            // Use Long before multiplication so unusual/invalid device reports cannot overflow.
            val heapBytes = memoryClassMb.coerceAtLeast(16).toLong() * MIB
            val memory = (heapBytes / 8).coerceIn(2L * MIB, (if (lowRam) 8L else 32L) * MIB)
            return ImageCacheBudget(memory.toInt(), (if (lowRam) 32L else 96L) * MIB,
                if (lowRam) 4 else 8, if (lowRam) 2 else 4, if (lowRam) 1 else 2)
        }
    }
}