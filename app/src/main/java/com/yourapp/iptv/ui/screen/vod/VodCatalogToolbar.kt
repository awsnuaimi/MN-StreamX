package com.yourapp.iptv.ui.screen.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.ui.component.common.ContextFocus
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

/** Persistent focus IDs: back=0, refresh=1, query=2. */
@Composable
internal fun VodCatalogToolbar(kind: VodKind, count: Int, query: String, labels: VodLabels,
    focus: ContextFocus, loading: Boolean, available: Boolean, onBack: () -> Unit, onRefresh: () -> Unit,
    onQuery: (String) -> Unit, onCategories: () -> Unit, sorted: Boolean,
    onSort: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onBack,
            modifier = Modifier.then(focus.target("controls", "back", 0)).tvFocusIndicator()
                .testTag("vod-back"), contentPadding = PaddingValues(horizontal = 12.dp),
            border = BorderStroke(1.dp, colors.outlineVariant)) { Text("‹  ${labels.back()}") }
        Text(labels.title(kind), style = MaterialTheme.typography.headlineSmall)
        Text("$count ${labels.titles()}", color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        if (available) {
            OutlinedTextField(value = query, onValueChange = onQuery, singleLine = true,
                modifier = Modifier.width(178.dp).height(56.dp)
                    .then(focus.target("controls", "query", 2)).testTag("vod-query"),
                placeholder = { Text(labels.search(), style = MaterialTheme.typography.bodySmall) },
                shape = RoundedCornerShape(24.dp), textStyle = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onCategories, modifier = Modifier.tvFocusIndicator()
                .testTag("vod-filter"), border = BorderStroke(1.dp, colors.outlineVariant),
                contentPadding = PaddingValues(horizontal = 13.dp)) { Text(labels.filter()) }
            OutlinedButton(onClick = onSort, modifier = Modifier.tvFocusIndicator()
                .testTag("vod-sort"), border = BorderStroke(1.dp,
                    if (sorted) colors.primary else colors.outlineVariant),
                contentPadding = PaddingValues(horizontal = 13.dp)) { Text(labels.sort()) }
        }
        if (!loading) {
            OutlinedButton(onClick = onRefresh,
                modifier = Modifier.then(focus.target("controls", "refresh", 1))
                    .tvFocusIndicator().testTag("vod-refresh"),
                border = BorderStroke(1.dp, colors.outlineVariant),
                contentPadding = PaddingValues(horizontal = 12.dp)) { Text("↻") }
        }
    }
}

/** Narrow in-catalog rail; only documented actions are interactive. */
@Composable
internal fun VodCatalogRail(kind: VodKind, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.width(58.dp).fillMaxHeight().background(colors.surfaceContainerLowest)
        .padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("X", color = colors.primary, style = MaterialTheme.typography.titleLarge)
        // Navigating out remains possible via the standard back button and remote Back.
        Surface(onClick = onBack, shape = RoundedCornerShape(14.dp), color = colors.surfaceContainer,
            modifier = Modifier.size(44.dp).tvFocusIndicator().testTag("vod-rail-back")) {
            Box(contentAlignment = Alignment.Center) { Text("⌂", color = colors.onSurface) }
        }
        Box(Modifier.size(44.dp).background(colors.primary, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            Text(if (kind == VodKind.MOVIE) "▣" else "▤", color = colors.onPrimary)
        }
    }
}