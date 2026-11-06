package mnstudio.apps.mnstreamx.domain.search

import mnstudio.apps.mnstreamx.domain.model.VodCategory
import mnstudio.apps.mnstreamx.domain.model.VodItem

data class SearchVodCatalog(
    val index: SearchIndex<VodItem>,
    val categories: List<VodCategory>,
    val categoryScoped: Boolean
)