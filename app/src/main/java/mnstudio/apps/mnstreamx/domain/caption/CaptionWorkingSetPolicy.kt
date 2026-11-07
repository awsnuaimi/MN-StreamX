package mnstudio.apps.mnstreamx.domain.caption

import mnstudio.apps.mnstreamx.domain.repository.CaptionDeviceStats

/** Conservative opt-in to retaining both inference stages; unknown/32-bit TVs stay serial. */
internal object CaptionWorkingSetPolicy {
    fun retain(stats: CaptionDeviceStats?): Boolean = stats != null &&
        stats.abi in setOf("arm64-v8a", "x86_64") && stats.totalMemoryMb >= 3072 && stats.availableMemoryMb >= 1024
    fun initialWindow(stats: CaptionDeviceStats?): Int = if (stats == null) 5000 else 3500
}