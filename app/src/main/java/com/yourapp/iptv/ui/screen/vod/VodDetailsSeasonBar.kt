package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodSeason
import com.yourapp.iptv.ui.component.common.ContextFocus
import com.yourapp.iptv.ui.component.common.tvFocusIndicator
import kotlinx.coroutines.launch

/** Each chip jumps within the existing LazyColumn; focus and playback remain unchanged. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VodDetailsSeasonBar(seasons: List<VodSeason>, labels: VodLabels,
    episodesList: LazyListState, focus: ContextFocus, offset: Int,
    selectedSeason: Int? = null, onChooseSeason: (Int) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.horizontalScroll(rememberScrollState()).testTag("vod-details-seasons"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        seasons.forEachIndexed { index, season ->
            val bring = remember(season.number) { BringIntoViewRequester() }
            val selected = selectedSeason == season.number
            OutlinedButton(
                onClick = {
                    onChooseSeason(season.number)
                    val row = VodDetailsSeasonNavigation.rowIndex(seasons, season.number)
                    if (row != null) scope.launch { episodesList.scrollToItem(row) }
                },
                modifier = Modifier.bringIntoViewRequester(bring)
                    .onFocusChanged { if (it.isFocused) scope.launch { bring.bringIntoView() } }
                    .then(focus.target("dialog", "season-jump:${season.number}", offset + index))
                    .semantics { this.selected = selected }
                    .tvFocusIndicator(VodCutShape).height(44.dp).widthIn(min = 90.dp)
                    .testTag("vod-season-jump-${season.number}"),
                shape = VodCutShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                    contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface),
                border = BorderStroke(if (selected) 2.dp else 1.dp,
                    if (selected) colors.primary else colors.outlineVariant)
            ) {
                Text(labels.text("الموسم", "Season", "Staffel") + " ${season.number}",
                    style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}