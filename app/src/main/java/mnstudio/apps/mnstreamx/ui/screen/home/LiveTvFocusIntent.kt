package mnstudio.apps.mnstreamx.ui.screen.home

/** Resolves remote intent without depending on an asynchronously recomposed selection. */
internal object LiveTvFocusIntent {
    /** An explicit pending move wins; otherwise use the actual focused row before UI selection. */
    fun position(
        pendingKey: String?,
        focusedKey: String?,
        activeKey: String?,
        count: Int,
        locate: (String) -> Int
    ): Int {
        if (count <= 0) return -1
        if (pendingKey != null) locate(pendingKey).takeIf { it in 0 until count }?.let { return it }
        if (focusedKey != null) locate(focusedKey).takeIf { it in 0 until count }?.let { return it }
        if (activeKey != null) locate(activeKey).takeIf { it in 0 until count }?.let { return it }
        return 0
    }

    /** Skip scroll animations only for page jumps, key repeats or a queued burst. */
    fun fastScroll(delta: Int, repeated: Boolean, pending: Boolean): Boolean =
        pending || repeated || delta < -1 || delta > 1
}