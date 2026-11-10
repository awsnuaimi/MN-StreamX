package mnstudio.apps.mnstreamx.ui.presentation

/** Limits only the disposable presentation caches, not the active channel catalog. */
internal data class LiveProjectionCacheBudget(
    val metadataEntries: Int,
    val reusableRows: Int,
    val preparedViews: Int,
    val preparedRows: Int
) {
    init {
        require(metadataEntries > 0 && reusableRows > 0 && preparedViews > 0 && preparedRows > 0)
    }

    companion object {
        /** memoryClass is the per-app Java heap class, not the device's physical RAM. */
        fun forDevice(memoryClassMb: Int, lowRam: Boolean): LiveProjectionCacheBudget = when {
            lowRam || memoryClassMb <= 96 -> LiveProjectionCacheBudget(2_048, 1_024, 1, 2_048)
            memoryClassMb <= 192 -> LiveProjectionCacheBudget(6_000, 4_096, 2, 6_000)
            else -> LiveProjectionCacheBudget(12_000, 12_000, 4, 12_000)
        }
    }
}