package mnstudio.apps.mnstreamx.ui.screen.vod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import mnstudio.apps.mnstreamx.domain.model.VodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stable, case-insensitive name order; does not mutate provider or progress lists. */
internal object VodCatalogOrder {
    const val BACKGROUND_THRESHOLD = 400
    fun byName(items: List<VodItem>): List<VodItem> =
        items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/** Small/test catalogs retain synchronous ordering; large sorts never block Compose. */
@Composable
internal fun rememberVodCatalogOrder(items: List<VodItem>, sortByName: Boolean): List<VodItem> {
    var result by remember(items, sortByName) {
        mutableStateOf(if (!sortByName || items.size >= VodCatalogOrder.BACKGROUND_THRESHOLD)
            items else VodCatalogOrder.byName(items))
    }
    LaunchedEffect(items, sortByName) {
        if (sortByName && items.size >= VodCatalogOrder.BACKGROUND_THRESHOLD) {
            val sorted = withContext(Dispatchers.Default) { VodCatalogOrder.byName(items) }
            result = sorted
        }
    }
    return result
}