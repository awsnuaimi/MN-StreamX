package com.yourapp.iptv.domain.search

import com.yourapp.iptv.domain.model.VodCategory
import com.yourapp.iptv.domain.model.VodItem

data class SearchVodCatalog(
    val index: SearchIndex<VodItem>,
    val categories: List<VodCategory>,
    val categoryScoped: Boolean
)