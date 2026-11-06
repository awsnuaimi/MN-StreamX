package mnstudio.apps.mnstreamx.domain.performance

enum class PerformanceStage { CACHE_READ, REFRESH, STORE_WRITE, XTREAM_ACCOUNT, XTREAM_LISTS, ORGANIZE,
    CHANNEL_LOOKUP, FIRST_FRAME, CATALOG_READ, HOME_READY, ACTIVITY_HOME, NAV_COMPOSE, PROFILE_COMMIT, HOME_FILTER,
    LIVE_PROJECT, LIVE_FOCUS, VOD_CACHE_READ, VOD_PROVIDER_FETCH, VOD_CACHE_WRITE,
    VOD_CATALOG_READY, VOD_CATEGORY_READY, VOD_CATEGORY_WARMUP,
    VOD_CACHE_MEMORY_LOOKUP, VOD_CACHE_DISK_LOAD, VOD_JSON_PARSE, VOD_GRID_LAYOUT, PRESENTATION_WAIT, PRESENTATION_WORK }
enum class PerformanceScreen { HOME, SEARCH, GUIDE, LIVE_PLAYER, VOD_LIST, VOD_PLAYER, SETTINGS,
    PROFILES, APPEARANCE, ORGANIZATION, BACKUP, DIAGNOSTICS, SUBSCRIPTIONS, PROTECTION, HISTORY }
enum class PerformanceOutcome { COMPLETE, FAILED, CANCELLED }

/** Fixed enums and bounded numeric data only; no route arguments or user identifiers. */
data class PerformanceSummary(val stage: PerformanceStage, val screen: PerformanceScreen?, val samples: Int,
    val complete: Int, val failed: Int, val cancelled: Int, val lastMs: Long, val p50Ms: Long?,
    val p95Ms: Long?, val maxMs: Long?, val clamped: Int) {
    fun valid(): Boolean {
        if ((stage == PerformanceStage.NAV_COMPOSE) != (screen != null) || samples !in 1..32) return false
        if (listOf(complete,failed,cancelled,clamped).any { it !in 0..samples } || complete + failed + cancelled != samples) return false
        if (lastMs !in 0..600_000) return false
        if (complete == 0) return p50Ms == null && p95Ms == null && maxMs == null
        val a = p50Ms ?: return false;val b = p95Ms ?: return false;val c = maxMs ?: return false
        return a in 0..600_000 && b in a..600_000 && c in b..600_000
    }
}