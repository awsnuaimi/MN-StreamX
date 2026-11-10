package mnstudio.apps.mnstreamx.ui.presentation

import mnstudio.apps.mnstreamx.domain.model.LiveTvPreferences
import mnstudio.apps.mnstreamx.ui.state.HomeUiState
import java.util.Locale

/** A result may stay visible only while membership, access and the browse context remain valid. */
internal class LiveProjectionContext(val state: HomeUiState, val preferences: LiveTvPreferences, val access: Long) {
    fun matches(other: LiveProjectionContext): Boolean = access == other.access &&
        state.browseScope == other.state.browseScope && state.channels === other.state.channels &&
        state.visibleChannels === other.state.visibleChannels && state.organization === other.state.organization && state.groups === other.state.groups &&
        state.selectedGroup == other.state.selectedGroup && state.query == other.state.query &&
        state.favoritesOnly == other.state.favoritesOnly && !state.isFiltering && !other.state.isFiltering &&
        preferences.hiddenChannels == other.preferences.hiddenChannels &&
        preferences.hiddenGroups == other.preferences.hiddenGroups && preferences.hiddenFolders == other.preferences.hiddenFolders
}
internal data class LiveBrowserResult(val input: Any, val context: LiveProjectionContext,
    val personal: HomeUiState, val index: LiveChannelIndex)

internal class LiveBrowserProjection(
    private val budget: LiveProjectionCacheBudget = LiveProjectionCacheBudget.forDevice(256, false)
) {
    private val metadata = LiveChannelMetadataCache(budget.metadataEntries)
    private val numbering = LiveChannelNumbers()
    private var personalContext: LiveProjectionContext? = null
    private var personal: HomeUiState? = null
    private val reusableRows = LinkedHashMap<String, LiveChannelRow>(16,.75f,true)
    private data class Prepared(val result: LiveBrowserResult, val sort: LiveChannelSort, val locale: Locale)
    private val prepared = ArrayList<Prepared>()

    fun project(input: Any, context: LiveProjectionContext, sort: LiveChannelSort, locale: Locale,
        checkpoint: () -> Unit): LiveBrowserResult {
        checkpoint()
        val state = context.state
        val preferences = context.preferences
        val previous = personalContext
        if (previous != null && (previous.access != context.access ||
                previous.state.browseScope != state.browseScope || previous.state.channels !== state.channels)) prepared.clear()
        val hit = prepared.indexOfFirst { it.sort == sort && it.locale == locale && it.result.context.matches(context) }
        if (hit >= 0) {
            val cached = prepared.removeAt(hit)
            prepared.add(cached)
            val result = cached.result
            return result.copy(input = input, context = context, personal = state.copy(
                channels = result.personal.channels, visibleChannels = result.personal.visibleChannels,
                groups = result.personal.groups, organization = result.personal.organization))
        }
        val sameBase = previous != null && previous.access == context.access &&
            previous.state.browseScope == state.browseScope && previous.state.channels === state.channels &&
            previous.state.groups === state.groups && previous.state.organization === state.organization &&
            previous.state.query == state.query && previous.state.favoritesOnly == state.favoritesOnly &&
            previous.preferences.hiddenChannels == preferences.hiddenChannels &&
            previous.preferences.hiddenGroups == preferences.hiddenGroups && previous.preferences.hiddenFolders == preferences.hiddenFolders
        val base = personal.takeIf { sameBase }
        val visible = if (base == null) LiveTvPersonalization.project(state,preferences,checkpoint) else state.copy(
            channels = base.channels,groups = base.groups,organization = base.organization,
            visibleChannels = state.visibleChannels.filter { checkpoint();preferences.visible(it) })
        val numbers = numbering.forCatalog(state.channels,metadata,checkpoint)
        val candidateRows = numbering.apply(
            LiveChannelRows.project(visible.visibleChannels, sort, locale, metadata, checkpoint), numbers)
        // Avoid churning a small LRU for every row of a much larger catalog.
        // The currently displayed projection retains its own rows independently.
        val rows = if (candidateRows.size > budget.reusableRows) {
            reusableRows.clear()
            candidateRows
        } else candidateRows.map { candidate ->
            checkpoint()
            val previousRow = reusableRows[candidate.preferenceKey]
            (previousRow?.takeIf { it == candidate } ?: candidate).also {
                reusableRows[candidate.preferenceKey] = it
                if (reusableRows.size > budget.reusableRows) {
                    reusableRows.entries.iterator().apply { next(); remove() }
                }
            }
        }
        checkpoint()
        personal = visible;personalContext = context
        return LiveBrowserResult(input,context,visible,LiveChannelIndex(rows)).also {
            prepared.add(Prepared(it,sort,locale))
            while (prepared.size > budget.preparedViews ||
                prepared.size > 1 && prepared.sumOf { value -> value.result.index.rows.size } > budget.preparedRows) {
                prepared.removeAt(0)
            }
        }
    }
}