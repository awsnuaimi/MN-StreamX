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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.VodKind
import com.yourapp.iptv.ui.component.common.ContextFocus
import com.yourapp.iptv.ui.component.common.tvFocusIndicator

/** All toolbar layouts keep back=0, refresh=1 and query=2 as stable focus targets. */
@Composable
internal fun VodCatalogToolbar(kind: VodKind, count: Int, query: String, labels: VodLabels,
    focus: ContextFocus, loading: Boolean, available: Boolean, onBack: () -> Unit, onRefresh: () -> Unit,
    onQuery: (String) -> Unit, onCategories: () -> Unit, sorted: Boolean,
    onSort: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("vod-toolbar")) {
        val mode = vodToolbarMode(maxWidth.value)
        when (mode) {
            VodToolbarMode.WIDE -> Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VodToolbarBack(focus, labels, onBack)
                VodToolbarTitle(kind, labels, Modifier.weight(1f, fill = false))
                VodToolbarCount(count, labels)
                Spacer(Modifier.weight(1f))
                if (available) {
                    VodToolbarQuery(query, labels, focus, onQuery, Modifier.width(178.dp))
                    VodToolbarFilter(labels, onCategories)
                    VodToolbarSort(labels, sorted, onSort)
                }
                if (!loading) VodToolbarRefresh(focus, onRefresh)
            }
            VodToolbarMode.STACKED -> Column(Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VodToolbarBack(focus, labels, onBack)
                    VodToolbarTitle(kind, labels, Modifier.weight(1f))
                    if (maxWidth.value >= 460f) VodToolbarCount(count, labels)
                    if (!loading) VodToolbarRefresh(focus, onRefresh)
                }
                if (available) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VodToolbarQuery(query, labels, focus, onQuery, Modifier.weight(1f))
                    VodToolbarFilter(labels, onCategories)
                    VodToolbarSort(labels, sorted, onSort)
                }
            }
            VodToolbarMode.NARROW -> Column(Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VodToolbarBack(focus, labels, onBack)
                    VodToolbarTitle(kind, labels, Modifier.weight(1f))
                    if (!loading) VodToolbarRefresh(focus, onRefresh)
                }
                if (available) {
                    VodToolbarQuery(query, labels, focus, onQuery, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VodToolbarFilter(labels, onCategories)
                        VodToolbarSort(labels, sorted, onSort)
                    }
                }
            }
        }
    }
}

@Composable
private fun VodToolbarTitle(kind: VodKind, labels: VodLabels, modifier: Modifier = Modifier) {
    Text(labels.title(kind), modifier = modifier, maxLines = 1,
        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun VodToolbarCount(count: Int, labels: VodLabels) {
    Text("$count ${labels.titles()}", color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun VodToolbarBack(focus: ContextFocus, labels: VodLabels, onBack: () -> Unit) {
    OutlinedButton(onClick = onBack,
        modifier = Modifier.then(focus.target("controls", "back", 0)).tvFocusIndicator()
            .testTag("vod-back"), contentPadding = PaddingValues(horizontal = 12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text("‹  ${labels.back()}", maxLines = 1)
    }
}

@Composable
private fun VodToolbarQuery(query: String, labels: VodLabels, focus: ContextFocus,
    onQuery: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value = query, onValueChange = onQuery, singleLine = true,
        modifier = modifier.height(56.dp).then(focus.target("controls", "query", 2))
            .testTag("vod-query"),
        placeholder = { Text(labels.search(), style = MaterialTheme.typography.bodySmall) },
        shape = RoundedCornerShape(24.dp), textStyle = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun VodToolbarFilter(labels: VodLabels, onCategories: () -> Unit) {
    OutlinedButton(onClick = onCategories, modifier = Modifier.tvFocusIndicator()
        .testTag("vod-filter"), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 13.dp)) { Text(labels.filter(), maxLines = 1) }
}

@Composable
private fun VodToolbarSort(labels: VodLabels, sorted: Boolean, onSort: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(onClick = onSort, modifier = Modifier.tvFocusIndicator()
        .testTag("vod-sort"), border = BorderStroke(1.dp,
        if (sorted) colors.primary else colors.outlineVariant),
        contentPadding = PaddingValues(horizontal = 13.dp)) { Text(labels.sort(), maxLines = 1) }
}

@Composable
private fun VodToolbarRefresh(focus: ContextFocus, onRefresh: () -> Unit) {
    OutlinedButton(onClick = onRefresh,
        modifier = Modifier.then(focus.target("controls", "refresh", 1))
            .tvFocusIndicator().testTag("vod-refresh"),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 12.dp)) { Text("↻") }
}

/** Narrow in-catalog rail; only documented actions are interactive. */
@Composable
internal fun VodCatalogRail(kind: VodKind, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.width(58.dp).fillMaxHeight().background(colors.surfaceContainerLowest)
        .padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("X", color = colors.primary, style = MaterialTheme.typography.titleLarge)
        Surface(onClick = onBack, shape = RoundedCornerShape(14.dp), color = colors.surfaceContainer,
            modifier = Modifier.size(44.dp).tvFocusIndicator().testTag("vod-rail-back")) {
            Box(contentAlignment = Alignment.Center) {
                VodRailIcon(VodRailGlyph.HOME, colors.onSurface, Modifier.size(24.dp))
            }
        }
        Box(Modifier.size(44.dp).background(colors.primary, VodCutShape),
            contentAlignment = Alignment.Center) {
            VodRailIcon(if (kind == VodKind.MOVIE) VodRailGlyph.MOVIE else VodRailGlyph.SERIES,
                colors.onPrimary, Modifier.size(24.dp))
        }
    }
}