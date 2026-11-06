package mnstudio.apps.mnstreamx.ui.state

import mnstudio.apps.mnstreamx.domain.model.Channel
import mnstudio.apps.mnstreamx.domain.model.VodItem

data class SearchVodState(
    val results: List<VodItem> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val searching: Boolean = false,
    val error: Boolean = false,
    val unavailable: Boolean = false,
    val categoryScoped: Boolean = false,
    val categoriesTotal: Int = 0,
    val categoriesSearched: Int = 0,
    val categoriesFailed: Int = 0,
    val scanning: Boolean = false,
    val more: Boolean = false,
    val indexLimited: Boolean = false
) {
    val canScan: Boolean get() = loaded && categoryScoped && !loading && !scanning && !more &&
        categoriesSearched < categoriesTotal
    val complete: Boolean get() = loaded && !loading && !error && !unavailable && !indexLimited &&
        (!categoryScoped || categoriesSearched == categoriesTotal)
}

data class SearchUiState(
    val query: String = "",
    val sourceKey: String? = null,
    val sourceLoading: Boolean = true,
    val sourceError: Boolean = false,
    val needsSource: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val channelsLoading: Boolean = false,
    val channelsSearching: Boolean = false,
    val channelsMissing: Boolean = false,
    val channelsError: Boolean = false,
    val channelsMore: Boolean = false,
    val channelsLimited: Boolean = false,
    val history: List<String> = emptyList(),
    val historyError: Boolean = false,
    val movies: SearchVodState = SearchVodState(),
    val series: SearchVodState = SearchVodState()
)