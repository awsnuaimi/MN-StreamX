package mnstudio.apps.mnstreamx.ui.screen.home

/**
 * Serial logo fallback for one projected channel family.
 *
 * Coil callbacks can outlive the request they describe. Only the active attempt may
 * advance the index, and a successful image may not be replaced by a late error.
 */
internal object LiveTvLogoFallbackPolicy {
    fun acceptsSuccess(activeIndex: Int, callbackIndex: Int): Boolean =
        activeIndex == callbackIndex

    fun nextIndex(
        activeIndex: Int,
        callbackIndex: Int,
        candidateCount: Int,
        alreadyLoaded: Boolean,
    ): Int {
        if (alreadyLoaded || activeIndex != callbackIndex) return activeIndex
        return (activeIndex + 1).coerceAtMost(candidateCount.coerceAtLeast(0))
    }
}