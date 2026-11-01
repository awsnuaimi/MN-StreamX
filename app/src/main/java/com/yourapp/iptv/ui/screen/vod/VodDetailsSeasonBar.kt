package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodSeason
import com.yourapp.iptv.ui.component.common.ContextFocus
import com.yourapp.iptv.ui.component.common.tvFocusIndicator
import kotlinx.coroutines.launch

/** Each chip jumps within the existing LazyColumn; focus and playback remain unchanged. */
@Composable
internal fun VodDetailsSeasonBar(seasons: List<VodSeason>, labels: VodLabels,
    episodesList: LazyListState, focus: ContextFocus, offset: Int) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier.horizontalScroll(rememberScrollState()).testTag("vod-details-seasons"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        seasons.forEachIndexed { index, season ->
            OutlinedButton(
                onClick = {
                    val row = VodDetailsSeasonNavigation.rowIndex(seasons, season.number)
                    if (row != null) scope.launch { episodesList.scrollToItem(row) }
                },
                modifier = focus.target("dialog", "season-jump:${season.number}", offset + index)
                    .tvFocusIndicator(VodCutShape).height(44.dp).widthIn(min = 90.dp)
                    .testTag("vod-season-jump-${season.number}"),
                shape = VodCutShape
            ) {
                Text(labels.text("الموسم", "Season", "Staffel") + " ${season.number}",
                    style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}