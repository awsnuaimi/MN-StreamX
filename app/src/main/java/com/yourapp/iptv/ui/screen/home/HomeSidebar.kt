package com.yourapp.iptv.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.yourapp.iptv.domain.model.HomeAppearance

@Composable
internal fun HomeSidebar(appearance: HomeAppearance, labels: HomeLabels, selected: HomeSection,
    loading: Boolean, focusRequesters: Map<HomeSection, FocusRequester>,
    onFocused: (HomeSection) -> Unit, onSelect: (HomeSection) -> Unit,
    onToggleTheme: () -> Unit, themeSaving: Boolean, themeError: Boolean,
    dashboardFocus: FocusRequester? = null, modifier: Modifier = Modifier,
    organizationReady: Boolean = true) {
    val colors = HomeTvPalettes.forMode(appearance.isDarkTheme)
    var hasFocus by remember { mutableStateOf(false) }
    val expanded = appearance.expandedSidebar || hasFocus
    val width = if (expanded) 180.dp else 70.dp
    Column(modifier.width(width).fillMaxHeight().background(colors.sidebar)
        .onFocusChanged { hasFocus = it.hasFocus }.focusGroup().padding(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (expanded) "MN StreamX" else "MN",
                Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                color = colors.accent, style = MaterialTheme.typography.titleMedium,
                maxLines = 1)
            HomeSection.entries.forEach { section ->
                var focused by remember(section) { mutableStateOf(false) }
                val active = selected == section
                Surface(onClick = { onSelect(section) },
                    enabled = !(loading && section == HomeSection.REFRESH) &&
                        (section != HomeSection.ORGANIZE || organizationReady),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        .focusRequester(focusRequesters.getValue(section))
                        .then(if (selected == HomeSection.DASHBOARD && dashboardFocus != null)
                            Modifier.focusProperties {
                                if (appearance.sidebarOnRight) left = dashboardFocus
                                else right = dashboardFocus
                            } else Modifier)
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onFocused(section)
                        }.semantics { this.selected = active; contentDescription = labels.section(section) },
                    color = when {
                        focused -> colors.focused
                        active -> colors.selected
                        else -> colors.sidebar
                    }, shape = MaterialTheme.shapes.medium,
                    border = when {
                        focused -> BorderStroke(2.dp, colors.accent)
                        active -> BorderStroke(1.dp, colors.selectedOutline)
                        else -> null
                    }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSidebarIcon(section, if (active || focused) colors.accent else colors.muted)
                        if (expanded) Text(labels.section(section), color = colors.text,
                            style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        var focusedTheme by remember { mutableStateOf(false) }
        val switchLabel = if (appearance.isDarkTheme)
            labels.text("الوضع النهاري", "Light mode", "Helles Design")
        else labels.text("الوضع الليلي", "Dark mode", "Dunkles Design")
        Surface(onClick = onToggleTheme, enabled = !themeSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .onFocusChanged { focusedTheme = it.isFocused }
                .semantics { contentDescription = switchLabel },
            color = if (focusedTheme) colors.focused else colors.sidebar,
            shape = MaterialTheme.shapes.medium,
            border = if (focusedTheme) BorderStroke(2.dp, colors.accent) else null) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (appearance.isDarkTheme) "☀" else "☾",
                    color = colors.accent, style = MaterialTheme.typography.titleMedium)
                if (expanded) Text(switchLabel, color = colors.text,
                    style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        if (themeError && expanded) Text(
            labels.text("تعذّر حفظ المظهر", "Could not save theme", "Design konnte nicht gespeichert werden"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }
}